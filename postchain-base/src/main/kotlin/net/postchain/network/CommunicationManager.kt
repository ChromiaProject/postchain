// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network

import net.postchain.core.NodeRid
import net.postchain.network.common.LazyPacket

interface CommunicationManager<PacketType> {
    fun init()

    //fun peerMap(): Map<NodeRid, PeerInfo>
    fun getPackets(): MutableList<Pair<NodeRid, PacketType>>
    fun sendPacket(packet: PacketType, recipient: NodeRid)
    fun sendPacket(packet: PacketType, recipients: List<NodeRid>)

    /**
     * @param packet is the data to send
     * @param oldPacket an old encoded packet to be used instead of encoding the packet
     * @return the encoded and signed packet
     */
    fun broadcastPacket(packet: PacketType, oldPacket: LazyPacket? = null): LazyPacket

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
