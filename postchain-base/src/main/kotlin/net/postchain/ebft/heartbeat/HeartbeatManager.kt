package net.postchain.ebft.heartbeat

import mu.KLogging
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

    companion object : KLogging()

    private val listeners: MutableSet<HeartbeatListener> =
        Collections.newSetFromMap(ConcurrentHashMap<HeartbeatListener, Boolean>())

    override fun addListener(listener: HeartbeatListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: HeartbeatListener) {
        listeners.remove(listener)
    }

    override fun beat(timestamp: Long) {
        /* TODO: [POS-164]: For manual test only. Delete this later
        if ((counter++ / 25) % 2 == 0) {
            logger.debug { "Heartbeat event received and skipped: timestamp $timestamp" }
            return
        } else {
            logger.debug { "Heartbeat event received and will be processed: timestamp $timestamp" }
        }
         */

        val event = HeartbeatEvent(timestamp)
        logger.debug { "Heartbeat event received: timestamp $timestamp" }
        listeners.forEach {
            it.onHeartbeat(event)
            logger.debug { "Heartbeat event sent: timestamp $timestamp" }
        }
    }
}