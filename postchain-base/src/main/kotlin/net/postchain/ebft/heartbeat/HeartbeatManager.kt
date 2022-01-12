package net.postchain.ebft.heartbeat

import mu.KLogging
import net.postchain.config.node.NodeConfig
import java.util.*
import java.util.concurrent.ConcurrentHashMap

interface HeartbeatManager {

    /**
     * Adds a listener of [HeartbeatEvent]
     */
    fun addListener(listener: HeartbeatListener)

    /**
     * Removes a listener of [HeartbeatEvent]
     */
    fun removeListener(listener: HeartbeatListener)

    /**
     * Sends heartbeat to listeners
     */
    fun beat(timestamp: Long)
}


class DefaultHeartbeatManager(val nodeConfig: NodeConfig) : HeartbeatManager {

    companion object : KLogging()

    private val listeners: MutableSet<HeartbeatListener> =
            Collections.newSetFromMap(ConcurrentHashMap<HeartbeatListener, Boolean>())

    override fun addListener(listener: HeartbeatListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: HeartbeatListener) {
        listeners.remove(listener)
    }

    private var heartbeatTestmodeCounter = 0
    override fun beat(timestamp: Long) {
        // TODO: [POS-164]: For manual test only. Delete this later
        if (nodeConfig.heartbeatTestmode) {
            if ((heartbeatTestmodeCounter++ / 25) % 2 == 0) {
                logger.debug { "Heartbeat event received and skipped: timestamp $timestamp" }
                return
            }
        }

        val event = HeartbeatEvent(timestamp)
        logger.debug { "Heartbeat event received: timestamp $timestamp" }
        listeners.forEach {
            it.onHeartbeat(event)
        }
    }
}