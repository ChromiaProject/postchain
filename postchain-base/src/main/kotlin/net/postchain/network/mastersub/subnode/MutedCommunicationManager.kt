package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.NodeRid
import net.postchain.network.CommunicationManager
import net.postchain.network.PacketVersionFilter
import net.postchain.network.ReceivedPacket
import net.postchain.network.common.LazyPacket

class MutedCommunicationManager<PacketType> : CommunicationManager<PacketType> {

    companion object : KLogging()

    override fun init() {
        throw ProgrammerMistake(errorMessage())
    }

    override fun getPackets(): MutableList<ReceivedPacket<PacketType>> = mutableListOf()

    override fun shutdown() = Unit

    override fun getPeerPacketVersion(peerId: NodeRid): Long {
        throw ProgrammerMistake(errorMessage())
    }

    override fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): Pair<NodeRid?, Set<NodeRid>> {
        throw ProgrammerMistake(errorMessage())
    }

    override fun broadcastPacket(packet: PacketType, oldPackets: Map<Long, LazyPacket>?, allowedVersionsFilter: PacketVersionFilter?): Map<Long, LazyPacket> {
        throw ProgrammerMistake(errorMessage())
    }

    override fun sendPacket(packet: PacketType, recipients: List<NodeRid>) {
        throw ProgrammerMistake(errorMessage())
    }

    override fun sendPacket(packet: PacketType, recipient: NodeRid) {
        throw ProgrammerMistake(errorMessage())
    }

    private fun errorMessage() = "Illegal use of ${javaClass.name}"
}