package net.postchain.ebft.heartbeat

import mu.KLogging
import java.util.concurrent.ConcurrentHashMap

interface HeartbeatManager {

    /**
     * Adds a listener of [HeartbeatEvent]
     */
    fun addListener(chainId: Long, listener: HeartbeatListener)

    /**
     * Removes a listener of [HeartbeatEvent] by [chainId] key
     */
    fun removeListener(chainId: Long)

    /**
     * Sends heartbeat to listeners
     */
    fun beat(timestamp: Long)
}


class DefaultHeartbeatManager : HeartbeatManager {

    companion object : KLogging()

    private val listeners: MutableMap<Long, HeartbeatListener> = ConcurrentHashMap()

    override fun addListener(chainId: Long, listener: HeartbeatListener) {
        listeners[chainId] = listener
    }

    override fun removeListener(chainId: Long) {
        listeners.remove(chainId)
    }

    override fun beat(timestamp: Long) {
        val event = HeartbeatEvent(timestamp)
        logger.debug { "Heartbeat event received: timestamp $timestamp" }
        listeners.values.forEach {
            it.onHeartbeat(event)
        }
    }
}