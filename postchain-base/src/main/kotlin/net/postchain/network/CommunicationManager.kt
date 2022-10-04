// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network

import net.postchain.core.NodeRid

interface CommunicationManager<PacketType> {
    fun init()

    //fun peerMap(): Map<NodeRid, PeerInfo>
    fun getPackets(): MutableList<Pair<NodeRid, PacketType>>
    fun sendPacket(packet: PacketType, recipient: NodeRid)
    fun broadcastPacket(packet: PacketType)

    /**
     * Sends the packet to a peer selected by random.
     *
     * @param amongPeers consider only these peers. The random choice will thus be made from the intersection of
     * amongPeers and connected peers.
     * @return the selected peer that the packet was sent to. If there
     * were no peers available, null is returned. Also, a set of the supplied peers that were actually connected
     */
    fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): Pair<NodeRid?, Set<NodeRid>>
    fun shutdown()
}
