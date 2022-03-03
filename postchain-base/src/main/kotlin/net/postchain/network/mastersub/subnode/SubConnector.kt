package net.postchain.network.mastersub.subnode

import net.postchain.base.PeerInfo
import net.postchain.core.Shutdownable

interface SubConnector : Shutdownable {
    fun connectMaster(
            masterNode: PeerInfo,
            connectionDescriptor: SubConnectionDescriptor)
}


