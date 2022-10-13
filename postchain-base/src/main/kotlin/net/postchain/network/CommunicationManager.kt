// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network

import net.postchain.core.NodeRid

interface CommunicationManager<PacketType> {
    fun init()

    //fun peerMap(): Map<NodeRid, PeerInfo>
    fun getPackets(): MutableList<Pair<NodeRid, PacketType>>
    fun sendPacket(packet: PacketType, recipient: NodeRid)
    fun sendPacket(packet: PacketType, recipients: List<NodeRid>)
    fun broadcastPacket(packet: PacketType)

    /**
     * Sends the packet to a peer selected by random.
     *
     * @param packet is the data to send
     * @param amongPeers consider only these peers. The random choice will thus be made from the intersection of
     * amongPeers and connected peers.
     * @return Firstly a randomly picked peer from the given set that has an open connection, or "null" if none found.
     * Secondly, the subset of acceptable peers that were actually considered as candidates for random selection
     */
    fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): Pair<NodeRid?, Set<NodeRid>>
    fun shutdown()
}
