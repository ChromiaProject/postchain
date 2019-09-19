package net.postchain.network.x

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.toHex
import net.postchain.devtools.PeerNameHelper.peerName
import net.postchain.network.CommunicationManager
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder

class DefaultXCommunicationManager<PacketType>(
        val connectionManager: XConnectionManager,
        val config: PeerCommConfiguration,
        val chainID: Long,
        val blockchainRID: ByteArray,
        private val packetEncoder: XPacketEncoder<PacketType>,
        private val packetDecoder: XPacketDecoder<PacketType>,
        val processName: String = ""
) : CommunicationManager<PacketType> {

    companion object : KLogging()

    private var inboundPackets = mutableListOf<Pair<XPeerID, PacketType>>()

    override fun init() {
        val peerConfig = XChainPeerConfiguration(
                chainID,
                blockchainRID,
                config,
                { data: ByteArray, peerID: XPeerID -> decodeAndEnqueue(peerID, data) },
                packetEncoder,
                packetDecoder
        )

        connectionManager.connectChain(peerConfig, true)
    }

    @Synchronized
    override fun getPackets(): MutableList<Pair<XPeerID, PacketType>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf()
        return currentQueue
    }

    override fun sendPacket(packet: PacketType, recipient: XPeerID) {
        logger.trace { "[$processName]: sendPacket($packet, ${peerName(recipient.toString())})" }

        require(XPeerID(config.pubKey) != recipient) {
            "CommunicationManager.sendPacket(): sender can not be the recipient"
        }

        connectionManager.sendPacket(
                { packetEncoder.encodePacket(packet) },
                chainID,
                recipient)
    }

    override fun broadcastPacket(packet: PacketType) {
        logger.trace { "[$processName]: broadcastPacket($packet)" }

        connectionManager.broadcastPacket(
                { packetEncoder.encodePacket(packet) },
                chainID)
    }

    override fun shutdown() {
        connectionManager.disconnectChain(chainID)
    }

    private fun decodeAndEnqueue(peerID: XPeerID, packet: ByteArray) {
        // packet decoding should not be synchronized so we can make
        // use of parallel processing in different threads
        logger.trace ("receiving a packet from peer: ${peerID.byteArray.toHex()}")
        val decodedPacket = packetDecoder.decodePacket(peerID.byteArray, packet)
        synchronized(this) {
            logger.trace("Successfully decoded the package, now adding it ")
            inboundPackets.add(peerID to decodedPacket)
        }
    }
}