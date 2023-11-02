// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BadDataException
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper.peerName
import net.postchain.ebft.message.EBFT_VERSION
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.MESSAGE_TYPE_TAG
import net.postchain.logging.SOURCE_NODE_TAG
import net.postchain.logging.TARGET_NODE_TAG
import net.postchain.network.CommunicationManager
import net.postchain.network.ReceivedPacket
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import net.postchain.network.common.ConnectionManager
import net.postchain.network.common.LazyPacket

class DefaultPeerCommunicationManager<PacketType>(
        val connectionManager: ConnectionManager,
        val config: PeerCommConfiguration,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val packetEncoder: XPacketEncoder<PacketType>,
        private val packetDecoder: XPacketDecoder<PacketType>,
        private val packetToString: (PacketType, Long) -> String
) : CommunicationManager<PacketType> {

    companion object : KLogging()

    private val myPeerId = config.myPeerInfo().pubKey.toHex()
    private val baseLoggingContext = arrayOf(
            BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
            CHAIN_IID_TAG to chainId.toString()
    )
    private val baseLoggingContextWithSender = arrayOf(
            *baseLoggingContext,
            SOURCE_NODE_TAG to myPeerId,
    )

    private var inboundPackets = mutableListOf<ReceivedPacket<PacketType>>()
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
    override fun getPackets(): MutableList<ReceivedPacket<PacketType>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf()
        return currentQueue
    }

    override fun sendPacket(packet: PacketType, recipient: NodeRid) {
        val packetVersion = getPeerPacketVersion(recipient)
        if (logger.isTraceEnabled) {
            withLoggingContext(
                    *baseLoggingContextWithSender,
                    TARGET_NODE_TAG to recipient.toHex(),
                    MESSAGE_TYPE_TAG to packet!!::class.java.simpleName
            ) {
                logger.trace { "sendPacket(${peerName(recipient.toString())}, ${packetToString(packet, packetVersion)}, $packetVersion)" }
            }
        }
        val encodingFunction = lazy { packetEncoder.encodePacket(packet, packetVersion) }
        sendEncodedPacket(encodingFunction, recipient)
    }

    override fun sendPacket(packet: PacketType, recipients: List<NodeRid>) {
        val encodedPackets: MutableMap<Long, LazyPacket> = mutableMapOf()
        recipients.forEach {
            val packetVersion = getPeerPacketVersion(it)
            if (logger.isTraceEnabled) {
                withLoggingContext(
                        *baseLoggingContextWithSender,
                        TARGET_NODE_TAG to it.toHex(),
                        MESSAGE_TYPE_TAG to packet!!::class.java.simpleName
                ) {
                    logger.trace { "sendPacket(${peerName(it.toString())}, ${packetToString(packet, packetVersion)}, $packetVersion)" }
                }
            }
            val lazyPacket = encodedPackets.getOrPut(packetVersion) { lazy { packetEncoder.encodePacket(packet, packetVersion) } }
            sendEncodedPacket(lazyPacket, it)
        }
    }

    override fun broadcastPacket(packet: PacketType, oldPackets: Map<Long, LazyPacket>?): Map<Long, LazyPacket> {
        if (logger.isTraceEnabled) {
            withLoggingContext(
                    *baseLoggingContextWithSender,
                    MESSAGE_TYPE_TAG to packet!!::class.java.simpleName
            ) {
                logger.trace { "broadcastPacket(${packetToString(packet, EBFT_VERSION)}, reusing=${oldPackets != null})" }
            }
        }

        val encodedPackets: MutableMap<Long, LazyPacket> = mutableMapOf()
        oldPackets?.let { encodedPackets.putAll(oldPackets) }
        val nodes = connectionManager.getConnectedNodes(chainId)
        nodes.forEach {
            val lazyPacket = encodedPackets.getOrPut(getPeerPacketVersion(it)) { lazy { packetEncoder.encodePacket(packet, getPeerPacketVersion(it)) } }
            sendEncodedPacket(lazyPacket, it)
        }
        return encodedPackets
    }

    override fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): Pair<NodeRid?, Set<NodeRid>> {
        val possiblePeers = connectionManager.getConnectedNodes(chainId).intersect(amongPeers)
        if (possiblePeers.isEmpty()) {
            return null to possiblePeers // We don't want to apply random to an empty list b/c throwing exception is too expensive.
        }
        val peer = possiblePeers.random()
        if (logger.isTraceEnabled) {
            withLoggingContext(
                    *baseLoggingContextWithSender,
                    TARGET_NODE_TAG to peer.toHex(),
                    MESSAGE_TYPE_TAG to packet!!::class.java.simpleName
            ) {
                logger.trace { "sendToRandomPeer(${peerName(peer.toString())}, ${packetToString(packet, getPeerPacketVersion(peer))})" }
            }
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

    override fun getPeerPacketVersion(peerId: NodeRid): Long = connectionManager.getPacketVersion(chainId, peerId)

    private fun sendEncodedPacket(lazyPacket: LazyPacket, recipient: NodeRid) {
        require(NodeRid(config.pubKey) != recipient) {
            "CommunicationManager.sendPacket(): sender can not be the recipient"
        }
        connectionManager.sendPacket(
                lazyPacket,
                chainId,
                recipient
        )
    }

    private fun consumePacket(packet: ByteArray, peerId: NodeRid) {
        try {
            /**
             * Packet decoding should not be synchronized, so we can make
             * use of parallel processing in different threads
             */
            val packetVersion = getPeerPacketVersion(peerId)
            val decodedPacket = packetDecoder.decodePacket(peerId.data, packet, packetVersion)
            synchronized(this) {
                if (logger.isTraceEnabled) {
                    withLoggingContext(
                            *baseLoggingContext,
                            SOURCE_NODE_TAG to peerId.toHex(),
                            TARGET_NODE_TAG to myPeerId,
                            MESSAGE_TYPE_TAG to decodedPacket!!::class.java.simpleName
                    ) {
                        logger.trace { "receivePacket(${peerId.toHex()}, ${packetToString(decodedPacket, packetVersion)}, $packetVersion)" }
                    }
                }
                inboundPackets.add(ReceivedPacket(peerId, packetVersion, decodedPacket))
            }
        } catch (e: BadMessageException) {
            logger.info("Bad message received from peer ${peerId}: ${e.message}")
        } catch (e: BadDataException) {
            logger.error("Error when receiving message from peer $peerId", e)
        }
    }
}