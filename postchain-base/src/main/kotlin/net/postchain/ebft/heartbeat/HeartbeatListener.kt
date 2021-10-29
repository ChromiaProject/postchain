package net.postchain.ebft.heartbeat

interface HeartbeatListener {

    val name: String

    /**
     * Called by [HeartbeatManager] to pass [HeartbeatEvent] to a client
     */
    fun onHeartbeat(heartbeatEvent: HeartbeatEvent)
}