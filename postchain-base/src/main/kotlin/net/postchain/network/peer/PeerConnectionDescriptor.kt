package net.postchain.network.peer

import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.IdentPacketInfo
import net.postchain.network.common.ConnectionDescriptor
import net.postchain.network.common.ConnectionDirection

/**
 * Describes a peer-2-peer connection
 */
class PeerConnectionDescriptor(
        val bcRid: BlockchainRid,
        val nodeId: NodeRid,
        val dir: ConnectionDirection
) : ConnectionDescriptor(bcRid) {

    fun isOutgoing() = (dir == ConnectionDirection.OUTGOING)

    fun loggingPrefix(): String {
        return BlockchainProcessName(
                nodeId.toString(),
                bcRid
        ).toString()
    }

}

/**
 * A way to create the [PeerConnectionDescriptor]
 */
object PeerConnectionDescriptorFactory {

    /**
     * @param identPacketInfo is the info we want to turn into a [PeerConnectionDescriptor]
     * @return the [PeerConnectionDescriptor] we created
     */
    fun createFromIdentPacketInfo(identPacketInfo: IdentPacketInfo): PeerConnectionDescriptor {
        return PeerConnectionDescriptor(
                identPacketInfo.blockchainRid,
                identPacketInfo.nodeId,
                ConnectionDirection.INCOMING
        )
    }

}
