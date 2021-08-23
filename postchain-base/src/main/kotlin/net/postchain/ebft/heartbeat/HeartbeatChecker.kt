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

open class Chain0HeartbeatChecker() : HeartbeatChecker {
    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) = Unit
    override fun checkHeartbeat(timestamp: Long) = true
}

open class DefaultHeartbeatChecker(val nodeConfig: NodeConfig, chainId: Long) : HeartbeatChecker {

    companion object : KLogging()

    private val resultLogger = ResultLogger()
    protected var heartbeat: HeartbeatEvent? = null
    protected val pref = "[chainId:${chainId}]:"

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        heartbeat = heartbeatEvent
        debug { "$pref Heartbeat event registered: $heartbeat" }
    }

    override fun checkHeartbeat(timestamp: Long): Boolean {
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

    protected fun debug(msg: () -> Any?) {
        if (logger.isDebugEnabled) {
            logger.debug(msg)
        }
    }
}