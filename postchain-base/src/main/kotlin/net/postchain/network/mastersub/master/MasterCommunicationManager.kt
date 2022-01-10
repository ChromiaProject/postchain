package net.postchain.network.mastersub.master

import net.postchain.core.Shutdownable
import net.postchain.ebft.heartbeat.HeartbeatEvent

interface MasterCommunicationManager : Shutdownable {
    fun init()
    fun sendHeartbeatToSub(heartbeatEvent: HeartbeatEvent)
}
