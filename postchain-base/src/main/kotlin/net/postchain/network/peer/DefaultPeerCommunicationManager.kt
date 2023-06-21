// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.core.BadDataException
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper.peerName
import net.postchain.network.CommunicationManager
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import net.postchain.network.common.ConnectionManager

class DefaultPeerCommunicationManager<PacketType>(
        val connectionManager: ConnectionManager,
        val config: PeerCommConfiguration,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val packetEncoder: XPacketEncoder<PacketType>,
        private val packetDecoder: XPacketDecoder<PacketType>
) : CommunicationManager<PacketType> {

    companion object : KLogging()

    private var inboundPackets = mutableListOf<Pair<NodeRid, PacketType>>()
    var connected = false

    /**
     * Main job during init() is to connect our chain using the [ConnectionManager].
     */
    @Synchronized
    override fun init() {
        if (connected) return

        val packetHandlerImpl = object : PeerPacketHandler {
            override fun handle(data: ByteArray, nodeId: NodeRid) {
                consumePacket(data, nodeId)
            }
        }
        val peerConfig = XChainPeersConfiguration(chainId, blockchainRid, config, packetHandlerImpl)
        connectionManager.connectChain(peerConfig, true)
        connected = true
    }

    @Synchronized
    override fun getPackets(): MutableList<Pair<NodeRid, PacketType>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf()
        return currentQueue
    }

    override fun sendPacket(packet: PacketType, recipient: NodeRid) {
        logger.trace { "sendPacket($packet, ${peerName(recipient.toString())})" }

        val encodingFunction = { packetEncoder.encodePacket(packet) }
        sendEncodedPacket(encodingFunction, recipient)
    }

    override fun sendPacket(packet: PacketType, recipients: List<NodeRid>) {
        val lazyPacket by lazy { packetEncoder.encodePacket(packet) }
        recipients.forEach {
            logger.trace { "sendPacket($packet, ${peerName(it.toString())})" }

            sendEncodedPacket({ lazyPacket }, it)
        }
    }

    override fun broadcastPacket(packet: PacketType) {
        logger.trace { "broadcastPacket($packet)" }

        val lazyPacket by lazy { packetEncoder.encodePacket(packet) }
        connectionManager.broadcastPacket(
                { lazyPacket },
                chainId
        )
    }

    override fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): Pair<NodeRid?, Set<NodeRid>> {
        val possiblePeers = connectionManager.getConnectedNodes(chainId).intersect(amongPeers)
        if (possiblePeers.isEmpty()) {
            return null to possiblePeers // We don't want to apply random to an empty list b/c throwing exception is too expensive.
        }
        val peer = possiblePeers.random()
        if (logger.isTraceEnabled) {
            logger.trace("sendToRandomPeer($packet, ${peerName(peer.toString())})")
        }
        return try {
            sendPacket(packet, peer)
            peer to possiblePeers
        } catch (e: Exception) {
            logger.error("Could not send package to random peer: ${peerName(peer)} because: ${e.message}", e)
            null to possiblePeers - peer
        }
    }

    @Synchronized
    override fun shutdown() {
        if (!connected) return
        connectionManager.disconnectChain(chainId)
        connected = false
    }

    private fun sendEncodedPacket(encodingFunction: () -> ByteArray, recipient: NodeRid) {
        require(NodeRid(config.pubKey) != recipient) {
            "CommunicationManager.sendPacket(): sender can not be the recipient"
        }

        connectionManager.sendPacket(
                encodingFunction,
                chainId,
                recipient
        )
    }

    private fun consumePacket(packet: ByteArray, peerId: NodeRid) {
        try {
            /**
             * Packet decoding should not be synchronized so we can make
             * use of parallel processing in different threads
             */
            logger.trace { "Receiving a packet from peer: ${peerId.toHex()}" }
            val decodedPacket = packetDecoder.decodePacket(peerId.data, packet)
            synchronized(this) {
                logger.trace { "Successfully decoded the package, now adding it " }
                inboundPackets.add(peerId to decodedPacket)
            }

        } catch (e: BadMessageException) {
            logger.info("Bad message received from peer ${peerId}: ${e.message}")
        } catch (e: BadDataException) {
            logger.error("Error when receiving message from peer $peerId", e)
        }
    }
}