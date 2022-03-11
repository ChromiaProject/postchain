package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.config.node.NodeConfig

open class Chain0HeartbeatListener() : HeartbeatListener {
    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) = Unit
    override fun checkHeartbeat(timestamp: Long) = true
}

open class DefaultHeartbeatListener(val nodeConfig: NodeConfig, chainId: Long) : HeartbeatListener {

    companion object : KLogging()

    private val resultLogger = ResultLogger()
    protected var heartbeat: HeartbeatEvent? = null
    protected val pref = "[chainId:${chainId}]:"

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        heartbeat = heartbeatEvent
        logger.debug { "$pref Heartbeat event registered: $heartbeat" }
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // First block check
        if (timestamp < 0) {
            return resultLogger.log(0 to true, logger) {
                "$pref Heartbeat check passed due to: timestamp = $timestamp < 0"
            }
        }

        // If heartbeat check is disabled, consider it as always passed
        if (!nodeConfig.heartbeatEnabled) {
            return resultLogger.log(1 to true, logger) {
                "$pref Heartbeat check passed due to: nodeConfig.heartbeat.enabled = ${nodeConfig.heartbeatEnabled}"
            }
        }

        // Heartbeat check is failed if there is no heartbeat event registered
        if (heartbeat == null) {
            return resultLogger.log(2 to false, logger) {
                "$pref Heartbeat check failed: no heartbeat event registered"
            }
        }

        val res = timestamp - heartbeat!!.timestamp < nodeConfig.heartbeatTimeout
        return resultLogger.log(3 to res, logger) {
            "$pref Heartbeat check result: $res (" +
                    "timestamp ($timestamp) - heartbeat.timestamp (${heartbeat!!.timestamp}) " +
                    "< nodeConfig.heartbeatTimeout (${nodeConfig.heartbeatTimeout}))"
        }
    }

}