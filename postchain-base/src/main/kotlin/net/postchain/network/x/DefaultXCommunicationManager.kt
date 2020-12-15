// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.toHex
import net.postchain.debug.BlockchainProcessName
import net.postchain.devtools.NameHelper.peerName
import net.postchain.network.CommunicationManager
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder

class DefaultXCommunicationManager<PacketType>(
        val connectionManager: XConnectionManager,
        val config: PeerCommConfiguration,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val packetEncoder: XPacketEncoder<PacketType>,
        private val packetDecoder: XPacketDecoder<PacketType>,
        protected val processName: BlockchainProcessName
) : CommunicationManager<PacketType> {

    companion object : KLogging()

    private var inboundPackets = mutableListOf<Pair<XPeerID, PacketType>>()

    override fun init() {
        val peerConfig = XChainPeersConfiguration(
                chainId,
                blockchainRid,
                config,
                { data: ByteArray, peerID: XPeerID -> consumePacket(peerID, data) },
                packetEncoder,
                packetDecoder
        )

        connectionManager.connectChain(peerConfig, true) { processName.toString() }
    }

    @Synchronized
    override fun getPackets(): MutableList<Pair<XPeerID, PacketType>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf()
        return currentQueue
    }

    override fun sendPacket(packet: PacketType, recipient: XPeerID) {
        logger.trace { "$processName: sendPacket($packet, ${peerName(recipient.toString())})" }

        require(XPeerID(config.pubKey) != recipient) {
            "CommunicationManager.sendPacket(): sender can not be the recipient"
        }

        connectionManager.sendPacket(
                { packetEncoder.encodePacket(packet) },
                chainId,
                recipient)
    }

    override fun broadcastPacket(packet: PacketType) {
        logger.trace { "$processName: broadcastPacket($packet)" }

        connectionManager.broadcastPacket(
                { packetEncoder.encodePacket(packet) },
                chainId)
    }

    override fun shutdown() {
        connectionManager.disconnectChain(chainId) { processName.toString() }
    }

    private fun consumePacket(peerId: XPeerID, packet: ByteArray) {
        /**
         * Packet decoding should not be synchronized so we can make
         * use of parallel processing in different threads
         */
        logger.trace("Receiving a packet from peer: ${peerId.byteArray.toHex()}")
        val decodedPacket = packetDecoder.decodePacket(peerId.byteArray, packet)
        synchronized(this) {
            logger.trace("Successfully decoded the package, now adding it ")
            inboundPackets.add(peerId to decodedPacket)
        }
    }
}