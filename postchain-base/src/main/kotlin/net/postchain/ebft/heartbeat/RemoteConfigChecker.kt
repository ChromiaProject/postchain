package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.protocol.*
import net.postchain.network.masterslave.slave.SlaveConnectionManager

class RemoteConfigChecker(
        nodeConfig: NodeConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val connectionManager: SlaveConnectionManager
) : DefaultHeartbeatChecker(nodeConfig, chainId), MsMessageHandler {

    companion object : KLogging()

    private val resultLogger = ResultLogger()
    private val intervalLogger = ResultLogger()

    lateinit var blockchainConfigProvider: BlockchainConfigurationProvider
    lateinit var storage: Storage
    private var responseTimestamp: Long = 0L

    init {
        connectionManager.setMsMessageHandler(chainId, this)
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // Check heartbeat
        val superCheck = super.checkHeartbeat(timestamp)
        if (!superCheck) return false

        // If remote config check is disabled, consider it as always passed
        if (!nodeConfig.remoteConfigEnabled) {
            return resultLogger.log(2 to true, logger) {
                "$pref RemoteConfig check passed due to: nodeConfig.remote_config.enabled = ${nodeConfig.remoteConfigEnabled}"
            }
        }

        // Check remote config
        val intervalCheck = timestamp - responseTimestamp > nodeConfig.remoteConfigRequestInterval
        val details = "timestamp ($timestamp) - responseTimestamp ($responseTimestamp) " +
                "> nodeConfig.remoteConfigRequestInterval (${nodeConfig.remoteConfigRequestInterval}) is $intervalCheck"
        if (intervalCheck) {
            intervalLogger.registerOnly(3 to intervalCheck)
            debug { "$pref Requesting of remote BlockchainConfig is required: $details" }

            val (height, nextHeight) = withReadConnection(storage, chainId) { ctx ->
                val height = DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
                height to blockchainConfigProvider.findNextConfigurationHeight(ctx, height)
            }
            val message = MsFindNextBlockchainConfigMessage(blockchainRid.data, height, nextHeight)
            connectionManager.sendMessageToMaster(chainId, message)
        } else {
            intervalLogger.log(3 to intervalCheck, logger) {
                "$pref Requesting of remote BlockchainConfig is NOT required: $details"
            }
        }

        val timeoutOccurred = timestamp - responseTimestamp > nodeConfig.remoteConfigTimeout
        return if (timeoutOccurred) {
            resultLogger.log(4 to false, logger) {
                "$pref Timeout check is failed: timestamp ($timestamp) - responseTimestamp ($responseTimestamp) >" +
                        " nodeConfig.remoteConfigTimeout (${nodeConfig.remoteConfigTimeout}) is true"
            }
        } else {
            resultLogger.log(4 to true, logger) {
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
                debug {
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
                    debug { "$pref Remote config is going to be stored: $details" }
                    withWriteConnection(storage, chainId) { ctx ->
                        BaseConfigurationDataStore.addConfigurationData(ctx, message.nextHeight!!, message.rawConfig)
                        true
                    }
                    debug { "$pref Remote config stored: $details" }
                } else {
                    debug { "$pref No new remote config: $details" }
                }
            }
        }
    }

}
