package net.postchain.network.masterslave.master

import net.postchain.core.Shutdownable

interface MasterCommunicationManager : Shutdownable {
    fun init()
}
