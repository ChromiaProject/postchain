package net.postchain.ebft.heartbeat

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


class DefaultHeartbeatManager : HeartbeatManager {

    private val listeners: MutableSet<HeartbeatListener> =
            Collections.newSetFromMap(ConcurrentHashMap<HeartbeatListener, Boolean>())

    override fun addListener(listener: HeartbeatListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: HeartbeatListener) {
        listeners.remove(listener)
    }

    override fun beat(timestamp: Long) {
        val event = HeartbeatEvent(timestamp)
        listeners.forEach { it.onHeartbeat(event) }
    }
}