package net.postchain.network.peer

import net.postchain.core.NodeRid


/**
 * Responsible for processing of incoming packets/messages from various peers
 */
interface PeerPacketHandler {

    /**
     * Handles a packet from a peer
     */
    fun handle(data: ByteArray, nodeId: NodeRid)

}