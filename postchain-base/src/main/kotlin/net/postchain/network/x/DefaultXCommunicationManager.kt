// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import mu.KLogging
import net.postchain.core.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.toHex
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
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

    var connected = false

    @Synchronized
    override fun init() {
        if (connected) return
        val peerConfig = XChainPeersConfiguration(chainId, blockchainRid, config) { data, peerId ->
            consumePacket(peerId, data)
        }
        connectionManager.connectChain(peerConfig, true) { processName.toString() }
        connected = true
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
                recipient
        )
    }

    override fun broadcastPacket(packet: PacketType) {
        logger.trace { "$processName: broadcastPacket($packet)" }

        connectionManager.broadcastPacket(
                { packetEncoder.encodePacket(packet) },
                chainId
        )
    }

    override fun sendToRandomPeer(packet: PacketType, amongPeers: Set<XPeerID>): XPeerID? {
        return try {
            val peer = connectionManager.getConnectedPeers(chainId).intersect(amongPeers).random()
            logger.trace { "$processName: sendToRandomPeer($packet, ${peerName(peer.toString())})" }
            sendPacket(packet, peer)
            peer
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    override fun shutdown() {
        if (!connected) return
        connectionManager.disconnectChain(chainId) { processName.toString() }
        connected = false
    }

    private fun consumePacket(peerId: XPeerID, packet: ByteArray) {
        try {
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
        } catch (e: BadDataMistake) {
            if (e.type == BadDataType.BAD_MESSAGE) {
                logger.info("Bad message received from peer ${peerId}: ${e.message}")
            } else {
                logger.error("Error when receiving message from peer $peerId", e)
            }
        }
    }
}