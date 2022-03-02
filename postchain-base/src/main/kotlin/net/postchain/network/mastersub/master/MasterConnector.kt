package net.postchain.network.mastersub.master

import net.postchain.core.Shutdownable

interface MasterConnector : Shutdownable {
    fun init(port: Int)
}


