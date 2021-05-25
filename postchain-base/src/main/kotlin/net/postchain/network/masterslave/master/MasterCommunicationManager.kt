package net.postchain.network.masterslave.master

import net.postchain.core.Shutdownable
import net.postchain.ebft.heartbeat.HeartbeatEvent

interface MasterCommunicationManager : Shutdownable {
    fun init()
    fun sendHeartbeatToSlave(heartbeatEvent: HeartbeatEvent)
}
