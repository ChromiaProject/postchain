package net.postchain.containers.bpm.bcconfig

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.Storage
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsFindNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager

class BlockWiseSubnodeBlockchainConfigListener(
        val appConfig: AppConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val connectionManager: SubConnectionManager
) : SubnodeBlockchainConfigListener, MsMessageHandler {

    companion object : KLogging()

    private val pref = "[chainId:${chainId}]:"
    lateinit var blockchainConfigProvider: BlockchainConfigurationProvider
    lateinit var storage: Storage

    //    private val lastHeight = AtomicLong(-1)
    private var lastHeight = -1L
    private val configVerifier = BlockchainConfigVerifier(appConfig)

    init {
        connectionManager.preAddMsMessageHandler(chainId, this)
    }

    @Synchronized
    override fun commit(height: Long, lastBlockTimestamp: Long) {
        if (lastHeight != -1L) {
            logger.error("Can't commit height $height, the current height $lastHeight is not reset yet")
        } else {
            lastHeight = height
            val nextHeight = withReadConnection(storage, chainId) { ctx ->
                blockchainConfigProvider.findNextConfigurationHeight(ctx, lastHeight)
            }
            val message = MsFindNextBlockchainConfigMessage(blockchainRid.data, lastHeight, nextHeight)
            connectionManager.sendMessageToMaster(chainId, message)
        }
    }

    override fun checkConfig(): Boolean {
        return lastHeight == -1L
    }

    @Synchronized
    override fun onMessage(message: MsMessage) {
        if (message is MsNextBlockchainConfigMessage) {
            val details = "brid: ${BlockchainRid(message.blockchainRid).toShortHex()}, " +
                    "chainId: $chainId, " +
                    "lastHeight: ${message.lastHeight}, " +
                    "nextHeight: ${message.nextHeight}, " +
                    "config length: ${message.rawConfig?.size}, " +
                    "config hash: ${message.configHash?.toHex()}"
            logger.debug { "$pref Remote BlockchainConfig received: $details" }

            if (lastHeight == message.lastHeight) {
                if (message.rawConfig != null && message.configHash != null) {
                    val approved = configVerifier.verify(message.rawConfig, message.configHash)
                    if (approved) {
                        logger.debug { "$pref Remote config will be stored: $details" }
                        withWriteConnection(storage, chainId) { ctx ->
                            DatabaseAccess.of(ctx).addConfigurationData(ctx, message.nextHeight!!, message.rawConfig)
                            true
                        }
                        lastHeight = -1L
                        logger.debug { "$pref Remote config stored: $details" }
                    } else {
                        logger.error { "$pref Remote config was corrupted and will not be stored: $details" }
                    }
                } else {
                    lastHeight = -1L
                    logger.debug { "$pref No new remote config: $details" }
                }
            } else {
                logger.error { "$pref Wrong response received. Current state: lastHeight: $lastHeight. Response: $details" }
            }
        }
    }
}