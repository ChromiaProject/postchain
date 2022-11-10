package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.base.BaseConfigurationDataStore
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.Storage
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsFindNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager

interface RemoteConfigListener {
    fun checkRemoteConfig(lastBlockTimestamp: Long): Boolean
}

class RemoteConfigHeartbeatListener(
        val heartbeatConfig: HeartbeatConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val connectionManager: SubConnectionManager
) : RemoteConfigListener, MsMessageHandler {

    companion object : KLogging()

    private val resultLogger = ResultLogger()
    private val intervalLogger = ResultLogger()
    private val pref = "[chainId:${chainId}]:"

    lateinit var blockchainConfigProvider: BlockchainConfigurationProvider
    lateinit var storage: Storage
    private var responseTimestamp: Long = 0L

    init {
        connectionManager.preAddMsMessageHandler(chainId, this)
    }

    override fun checkRemoteConfig(lastBlockTimestamp: Long): Boolean {
        // First block check
        if (lastBlockTimestamp < 0) {
            return resultLogger.log(1 to true, logger) {
                // We should skip remote config check in this case (there no blocks).
                // It's considered that subnode always have config for height 0
                "$pref Remote config check passed due to: timestamp = $lastBlockTimestamp < 0"
            }
        }

        // Check remote config
        val intervalCheck = lastBlockTimestamp - responseTimestamp > heartbeatConfig.remoteConfigRequestInterval
        val details = "timestamp ($lastBlockTimestamp) - responseTimestamp ($responseTimestamp) " +
                "> remoteConfigRequestInterval (${heartbeatConfig.remoteConfigRequestInterval}) is $intervalCheck"
        if (intervalCheck) {
            intervalLogger.registerOnly(2 to intervalCheck)
            logger.debug { "$pref Requesting of remote BlockchainConfig is required: $details" }

            val (height, nextHeight) = withReadConnection(storage, chainId) { ctx ->
                val height = DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
                height to blockchainConfigProvider.findNextConfigurationHeight(ctx, height)
            }
            val message = MsFindNextBlockchainConfigMessage(blockchainRid.data, height, nextHeight)
            connectionManager.sendMessageToMaster(chainId, message)
            logger.debug {
                "$pref Remote BlockchainConfig requested: " +
                        "blockchainRid: ${blockchainRid.toShortHex()}, " +
                        "height: $height, nextHeight: $nextHeight"
            }
        } else {
            intervalLogger.log(2 to intervalCheck, logger) {
                "$pref Requesting of remote BlockchainConfig is NOT required: $details"
            }
        }

        val timeoutOccurred = lastBlockTimestamp - responseTimestamp > heartbeatConfig.remoteConfigTimeout
        return if (timeoutOccurred) {
            resultLogger.log(3 to false, logger) {
                "$pref Timeout check is failed: timestamp ($lastBlockTimestamp) - responseTimestamp ($responseTimestamp) >" +
                        " remoteConfigTimeout (${heartbeatConfig.remoteConfigTimeout}) is true"
            }
        } else {
            resultLogger.log(3 to true, logger) {
                "$pref Remote config check is true"
            }
        }
    }

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsNextBlockchainConfigMessage -> {
                val details = "brid: ${BlockchainRid(message.blockchainRid).toShortHex()}, " +
                        "chainId: $chainId, " +
                        "height: ${message.nextHeight}, " +
                        "config length: ${message.rawConfig?.size}, " +
                        "config hash: ${message.configHash?.toHex()}"

                logger.debug { "$pref Remote BlockchainConfig received: $details" }

                if (message.rawConfig != null && message.configHash != null) {
                    val approved = RemoteConfigVerifier.verify(message.rawConfig, message.configHash)
                    if (approved) {
                        logger.debug { "$pref Remote config is going to be stored: $details" }
                        withWriteConnection(storage, chainId) { ctx ->
                            BaseConfigurationDataStore.addConfigurationData(ctx, message.nextHeight!!, message.rawConfig)
                            true
                        }
                        responseTimestamp = System.currentTimeMillis()
                        logger.debug { "$pref Remote config stored: $details" }
                    } else {
                        logger.debug { "$pref Remote config was corrupted and will not be stored: $details" }
                    }
                } else {
                    responseTimestamp = System.currentTimeMillis()
                    logger.debug { "$pref No new remote config: $details" }
                }
            }
        }
    }

}
