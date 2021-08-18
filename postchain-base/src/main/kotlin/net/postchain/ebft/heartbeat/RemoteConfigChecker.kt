package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.protocol.MsBlockchainConfigMessage
import net.postchain.network.masterslave.protocol.MsHeartbeatMessage
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.masterslave.slave.SlaveConnectionManager

class RemoteConfigChecker(
    nodeConfig: NodeConfig,
    val chainId: Long,
    val connectionManager: SlaveConnectionManager
) : DefaultHeartbeatChecker(nodeConfig), MsMessageHandler {

    companion object : KLogging()

    private val resultLogger = ResultLogger()
    private val intervalLogger = ResultLogger()

    lateinit var remoteConfigConsumer: (Long, ByteArray) -> Unit
    private var responseTimestamp: Long = 0L
    private var rawConfig: ByteArray? = null

    init {
        connectionManager.setMsMessageHandler(chainId, this)
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // Check heartbeat
        val superCheck = super.checkHeartbeat(timestamp)
        resultLogger.log(1 to superCheck, logger) {
            "DefaultHeartbeatChecker.checkHeartbeat(timestamp) = $superCheck"
        }
        if (!superCheck) return false

        // If remote config check is disabled, consider it as always passed
        if (!nodeConfig.remoteConfigEnabled) {
            return resultLogger.log(2 to true, logger) {
                "RemoteConfig check passed due to: nodeConfig.remote_config.enabled = ${nodeConfig.remoteConfigEnabled}"
            }
        }

        // Check remote config
        val intervalCheck = timestamp - responseTimestamp > nodeConfig.remoteConfigRequestInterval
        val details = "timestamp ($timestamp) - responseTimestamp ($responseTimestamp) " +
                "> nodeConfig.remoteConfigRequestInterval (${nodeConfig.remoteConfigRequestInterval}) is $intervalCheck"
        if (intervalCheck) {
            intervalLogger.registerOnly(3 to intervalCheck)
            debug { "Requesting of remote BlockchainConfig is required: $details" }
            connectionManager.requestBlockchainConfig(chainId)
        } else {
            intervalLogger.log(3 to intervalCheck, logger) {
                "Requesting of remote BlockchainConfig is NOT required: $details"
            }
        }

        val timeoutOccurred = timestamp - responseTimestamp > nodeConfig.remoteConfigTimeout
        return if (timeoutOccurred) {
            resultLogger.log(4 to false, logger) {
                "Timeout check is failed: timestamp ($timestamp) - responseTimestamp ($responseTimestamp) >" +
                        " nodeConfig.remoteConfigTimeout (${nodeConfig.remoteConfigTimeout}) is true"
            }
        } else {
            resultLogger.log(4 to true, logger) {
                "Heartbeat check is true"
            }
        }
    }

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsHeartbeatMessage -> {
                onHeartbeat(HeartbeatEvent(message.timestamp))
            }
            is MsBlockchainConfigMessage -> {
                debug {
                    "Remote BlockchainConfig received: " +
                            "chainId: $chainId, " +
                            "${BlockchainRid(message.blockchainRid).toShortHex()}, " +
                            "height: ${message.height}"
                }

                // TODO: Validate rawConfig here
                // ...

                rawConfig = message.payload
                responseTimestamp = System.currentTimeMillis()

                remoteConfigConsumer(0L, rawConfig!!) // TODO: [et]
            }
        }
    }

}
