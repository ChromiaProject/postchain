package net.postchain.ebft.heartbeat

import mu.KLogging
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

    lateinit var remoteConfigConsumer: (Long, ByteArray) -> Unit
    private var responseTimestamp: Long = 0L
    private var rawConfig: ByteArray? = null

    init {
        connectionManager.setMsMessageHandler(chainId, this)
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // Check heartbeat
        val superCheck = super.checkHeartbeat(timestamp)
        debug { "DefaultHeartbeatChecker.checkHeartbeat(timestamp) = $superCheck" }
        if (!superCheck) return false

        // If remote config check is disabled, consider it as always passed
        if (!nodeConfig.remoteConfigEnabled) {
            debug { "RemoteConfig check passed due to: nodeConfig.remote_config.enabled = ${nodeConfig.remoteConfigEnabled}" }
            return true
        }

        // Check remote config
        val intervalCheck = timestamp - responseTimestamp > nodeConfig.remoteConfigRequestInterval
        val details = "timestamp ($timestamp) - responseTimestamp ($responseTimestamp) " +
                "> nodeConfig.remoteConfigRequestInterval (${nodeConfig.remoteConfigRequestInterval}) is $intervalCheck"
        if (intervalCheck) {
            debug { "Requesting of remote BlockchainConfig is required: $details" }
            connectionManager.requestBlockchainConfig(chainId)
        } else {
            debug { "Requesting of remote BlockchainConfig is NOT required: $details" }
        }

        val timeoutCheck = timestamp - responseTimestamp > nodeConfig.remoteConfigTimeout
        debug {
            "Timeout check is failed: timestamp ($timestamp) - responseTimestamp ($responseTimestamp) >" +
                    " nodeConfig.remoteConfigTimeout (${nodeConfig.remoteConfigTimeout}) is $timeoutCheck"
        }
        if (timeoutCheck) return false

        debug { "Heartbeat check is true" }
        return true
    }

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsHeartbeatMessage -> {
                onHeartbeat(HeartbeatEvent(message.timestamp))
            }
            is MsBlockchainConfigMessage -> {
                // TODO: Validate rawConfig here
                // ...

                rawConfig = message.payload
                responseTimestamp = System.currentTimeMillis()

                remoteConfigConsumer(0L, rawConfig!!) // TODO: [et]
            }
        }
    }

}
