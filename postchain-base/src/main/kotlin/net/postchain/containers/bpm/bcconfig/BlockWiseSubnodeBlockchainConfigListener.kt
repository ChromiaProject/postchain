package net.postchain.containers.bpm.bcconfig

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.Storage
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsFindNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BlockWiseSubnodeBlockchainConfigListener(
        private val config: SubnodeBlockchainConfigurationConfig,
        private val configVerifier: BlockchainConfigVerifier,
        private val chainId: Long,
        private val blockchainRid: BlockchainRid,
        private val connectionManager: SubConnectionManager,
        private val blockchainConfigProvider: BlockchainConfigurationProvider,
        private val storage: Storage
) : SubnodeBlockchainConfigListener, MsMessageHandler {

    companion object : KLogging()

    private val pref = "[chainId:${chainId}]:"
    private val lock = ReentrantLock()
    private val receivedConfig = lock.newCondition()
    private val lastHeight = AtomicLong(-1L)

    init {
        if (config.enabled) {
            connectionManager.preAddMsMessageHandler(chainId, this)
        }
    }

    override fun commit(height: Long) {
        if (!config.enabled) return

        if (lastHeight.get() != -1L) {
            // This is an error, new block was committed before old block was done committing
            logger.error("Can't commit height $height, the current height ${lastHeight.get()} is not reset yet")
            return
        }

        lastHeight.set(height)
        val nextHeight = withReadConnection(storage, chainId) { ctx ->
            blockchainConfigProvider.findNextConfigurationHeight(ctx, height)
        }
        while (lastHeight.get() == height) {
            val message = MsFindNextBlockchainConfigMessage(blockchainRid.data, height, nextHeight)
            connectionManager.sendMessageToMaster(chainId, message)
            logger.debug { "$pref Waiting for Remote BlockchainConfig at height: $height" }
            lock.withLock {
                receivedConfig.await(config.sleepTimeout, TimeUnit.MILLISECONDS)
            }
            logger.debug { "$pref Checking if Remote BlockchainConfig was received for height: $height" }
        }
    }

    override fun onMessage(message: MsMessage) {
        if (!config.enabled) return

        if (message is MsNextBlockchainConfigMessage) {
            val details = "brid: ${BlockchainRid(message.blockchainRid).toShortHex()}, " +
                    "chainId: $chainId, " +
                    "lastHeight: ${message.lastHeight}, " +
                    "nextHeight: ${message.nextHeight}, " +
                    "config length: ${message.rawConfig?.size}, " +
                    "config hash: ${message.configHash?.toHex()}"
            logger.debug { "$pref Remote BlockchainConfig received: $details" }

            if (lastHeight.get() == message.lastHeight) {
                if (message.rawConfig != null && message.configHash != null) {
                    if (!configVerifier.verify(message.rawConfig, message.configHash)) {
                        logger.error { "$pref Remote config was corrupted and will not be stored: $details" }
                        return
                    }
                    try {
                        GTXBlockchainConfigurationFactory.validateConfiguration(
                                GtvDecoder.decodeGtv(message.rawConfig),
                                blockchainRid
                        )
                    } catch (e: Exception) {
                        logger.warn { "${e.message}" }
                        return
                    }

                    logger.debug { "$pref Remote config will be stored: $details" }
                    withWriteConnection(storage, chainId) { ctx ->
                        DatabaseAccess.of(ctx).addConfigurationData(ctx, message.nextHeight!!, message.rawConfig)
                        true
                    }
                    logger.debug { "$pref Remote config stored: $details" }
                } else {
                    logger.debug { "$pref No new remote config: $details" }
                }
                lastHeight.set(-1L)
                lock.withLock {
                    receivedConfig.signalAll()
                }
            } else {
                logger.error { "$pref Wrong response received. Current state: lastHeight: $lastHeight. Response: $details" }
            }
        }
    }
}