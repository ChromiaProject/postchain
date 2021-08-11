package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.config.node.NodeConfig

interface HeartbeatChecker {

    /**
     * Should be called to register a [HeartbeatEvent]
     */
    fun onHeartbeat(heartbeatEvent: HeartbeatEvent)

    /**
     * Should be called by client to compare [timestamp] with registered [HeartbeatEvent].
     * If there no registered [HeartbeatEvent] it returns false.
     * If [timestamp] exceeds [HeartbeatEvent.timestamp] by more than [NodeConfig.heartbeatTimeout]
     * it returns false, otherwise true.
     */
    fun checkHeartbeat(timestamp: Long): Boolean
}

open class DefaultHeartbeatChecker(val nodeConfig: NodeConfig) : HeartbeatChecker {

    companion object : KLogging()

    protected var heartbeat: HeartbeatEvent? = null

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        heartbeat = heartbeatEvent
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // If heartbeat check is disabled, consider it as always passed
        if (!nodeConfig.heartbeatEnabled) {
            debug { "Heartbeat check passed due to: nodeConfig.heartbeat.enabled = ${nodeConfig.heartbeatEnabled}" }
            return true
        }

        // Heartbeat check is failed if there is no heartbeat event registered
        if (heartbeat == null) {
            debug { "Heartbeat check failed: no heartbeat event registered" }
            return false
        }

        val res = timestamp - heartbeat!!.timestamp < nodeConfig.heartbeatTimeout
        debug {
            "Heartbeat check result: $res (" +
                    "timestamp ($timestamp) - heartbeat.timestamp (${heartbeat!!.timestamp}) " +
                    "< nodeConfig.heartbeatTimeout (${nodeConfig.heartbeatTimeout}))"
        }
        return res
    }

    protected fun debug(msg: () -> Any?) {
        if (logger.isDebugEnabled) {
            logger.debug(msg)
        }
    }
}