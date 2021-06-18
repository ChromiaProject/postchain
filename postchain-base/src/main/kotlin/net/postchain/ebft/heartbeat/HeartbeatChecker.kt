package net.postchain.ebft.heartbeat

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

class DefaultHeartbeatChecker(val nodeConfig: NodeConfig) : HeartbeatChecker {

    private var heartbeat: HeartbeatEvent? = null

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        heartbeat = heartbeatEvent
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
        // If heartbeat check is switched off, consider it as passed
        if (!nodeConfig.heartbeat) return true

        // Heartbeat check is failed if there is no registered heartbeat event.
        if (heartbeat == null) return false

        return timestamp - heartbeat!!.timestamp < nodeConfig.heartbeatTimeout
    }
}