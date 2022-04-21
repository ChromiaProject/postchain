package net.postchain.ebft.heartbeat

import mu.KLogging
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

open class DefaultHeartbeatListener(val heartbeatConfig: HeartbeatConfig, chainId: Long) : HeartbeatListener {

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
            return resultLogger.log(1 to true, logger) {
                "$pref Heartbeat check passed due to: timestamp = $timestamp < 0"
            }
        }

        // Heartbeat check is failed if there is no heartbeat event registered
        if (heartbeat == null) {
            return resultLogger.log(2 to false, logger) {
                "$pref Heartbeat check failed: no heartbeat event registered"
            }
        }

        val res = timestamp - heartbeat!!.timestamp < heartbeatConfig.timeout
        return resultLogger.log(3 to res, logger) {
            "$pref Heartbeat check result: $res (" +
                    "timestamp ($timestamp) - heartbeat.timestamp (${heartbeat!!.timestamp}) " +
                    "< nodeConfig.heartbeatTimeout (${heartbeatConfig.timeout}))"
        }
    }

}