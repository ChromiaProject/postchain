package net.postchain.ebft.heartbeat

import net.postchain.config.node.NodeConfig

interface HeartbeatListener {

    /**
     * Should be called to register a [HeartbeatEvent]
     */
    fun onHeartbeat(heartbeatEvent: HeartbeatEvent)

    /**
     * Should be called by client to compare [timestamp] with registered [HeartbeatEvent].
     *  - If there no registered [HeartbeatEvent] it returns false.
     *  - If [timestamp] exceeds [HeartbeatEvent.timestamp] by more than [NodeConfig.heartbeatTimeout]
     * it returns false, otherwise true.
     *  - If [timestamp] < 0 then it's considered there are no blocks and we should return true
     * to be able to build at least one block.
     */
    fun checkHeartbeat(timestamp: Long): Boolean
}