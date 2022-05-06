package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.base.BaseConfigurationDataStore
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.common.BlockchainRid
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.*
import net.postchain.network.mastersub.subnode.SubConnectionManager

class RemoteConfigHeartbeatListener(
        heartbeatConfig: HeartbeatConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val connectionManager: SubConnectionManager
) : DefaultHeartbeatListener(heartbeatConfig, chainId), MsMessageHandler {

    companion object : KLogging()

    private val resultLogger = ResultLogger()
    private val intervalLogger = ResultLogger()

    lateinit var blockchainConfigProvider: BlockchainConfigurationProvider
    lateinit var storage: Storage
    private var responseTimestamp: Long = 0L

    init {
        connectionManager.preAddMsMessageHandler(chainId, this)
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // First block check
        if (timestamp < 0) {
            return resultLogger.log(1 to true, logger) {
                // We should skip remote config check in this case (there no blocks).
                // It's considered that subnode always have config for height 0
                "$pref Heartbeat check passed due to: timestamp = $timestamp < 0"
            }
        }

        // Check heartbeat
        val superCheck = super.checkHeartbeat(timestamp)
        if (!superCheck) return false

        // Check remote config
        val intervalCheck = timestamp - responseTimestamp > heartbeatConfig.remoteConfigRequestInterval
        val details = "timestamp ($timestamp) - responseTimestamp ($responseTimestamp) " +
                "> heartbeatConfig.remoteConfigRequestInterval (${heartbeatConfig.remoteConfigRequestInterval}) is $intervalCheck"
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

        val timeoutOccurred = timestamp - responseTimestamp > heartbeatConfig.remoteConfigTimeout
        return if (timeoutOccurred) {
            resultLogger.log(3 to false, logger) {
                "$pref Timeout check is failed: timestamp ($timestamp) - responseTimestamp ($responseTimestamp) >" +
                        " heartbeatConfig.remoteConfigTimeout (${heartbeatConfig.remoteConfigTimeout}) is true"
            }
        } else {
            resultLogger.log(3 to true, logger) {
                "$pref Heartbeat check is true"
            }
        }
    }

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsHeartbeatMessage -> {
                onHeartbeat(HeartbeatEvent(message.timestamp))

                // Reply with subnode status message. For tests only.
                val height = withReadConnection(storage, chainId) { ctx ->
                    DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
                }
                val response = MsSubnodeStatusMessage(blockchainRid.data, height)
                connectionManager.sendMessageToMaster(chainId, response)
            }

            is MsNextBlockchainConfigMessage -> {
                logger.debug {
                    "$pref Remote BlockchainConfig received: " +
                            "chainId: $chainId, " +
                            "${BlockchainRid(message.blockchainRid).toShortHex()}, " +
                            "height: ${message.nextHeight}, " +
                            "remote config length: ${message.rawConfig?.size}"
                }

                // TODO: [POS-164]: Validate rawConfig here
                responseTimestamp = System.currentTimeMillis()

                val details = "chainId: $chainId, nextHeight: ${message.nextHeight}, remote config length: ${message.rawConfig?.size}"
                if (message.rawConfig != null) {
                    logger.debug { "$pref Remote config is going to be stored: $details" }
                    withWriteConnection(storage, chainId) { ctx ->
                        BaseConfigurationDataStore.addConfigurationData(ctx, message.nextHeight!!, message.rawConfig)
                        true
                    }
                    logger.debug { "$pref Remote config stored: $details" }
                } else {
                    logger.debug { "$pref No new remote config: $details" }
                }
            }
        }
    }

}
