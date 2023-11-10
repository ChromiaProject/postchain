// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BadDataException
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper.peerName
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.MESSAGE_TYPE_TAG
import net.postchain.logging.SOURCE_NODE_TAG
import net.postchain.logging.TARGET_NODE_TAG
import net.postchain.network.CommunicationManager
import net.postchain.network.PacketVersionFilter
import net.postchain.network.ReceivedPacket
import net.postchain.network.XPacketCodec
import net.postchain.network.common.ConnectionManager
import net.postchain.network.common.LazyPacket

class DefaultPeerCommunicationManager<PacketType>(
        val connectionManager: ConnectionManager,
        val config: PeerCommConfiguration,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val packetCodec: XPacketCodec<PacketType>,
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

    private val nodePacketVersions = mutableMapOf<NodeRid, Long>()
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
        val encodingFunction = lazy { packetCodec.encodePacket(packet, packetVersion) }
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
            val lazyPacket = encodedPackets.getOrPut(packetVersion) { lazy { packetCodec.encodePacket(packet, packetVersion) } }
            sendEncodedPacket(lazyPacket, it)
        }
    }

    override fun broadcastPacket(
            packet: PacketType,
            oldPackets: Map<Long, LazyPacket>?,
            allowedVersionsFilter: PacketVersionFilter?
    ): Map<Long, LazyPacket> {
        if (logger.isTraceEnabled) {
            withLoggingContext(
                    *baseLoggingContextWithSender,
                    MESSAGE_TYPE_TAG to packet!!::class.java.simpleName
            ) {
                logger.trace { "broadcastPacket(${packetToString(packet, packetCodec.getPacketVersion())}, reusing=${oldPackets != null})" }
            }
        }

        val encodedPackets: MutableMap<Long, LazyPacket> = mutableMapOf()
        oldPackets?.let { encodedPackets.putAll(oldPackets) }
        val nodes = connectionManager.getConnectedNodes(chainId)
        nodes.forEach {
            val peerPacketVersion = getPeerPacketVersion(it)
            if (allowedVersionsFilter == null || allowedVersionsFilter(peerPacketVersion)) {
                val lazyPacket = encodedPackets.getOrPut(peerPacketVersion) { lazy { packetCodec.encodePacket(packet, peerPacketVersion) } }
                sendEncodedPacket(lazyPacket, it)
            } else {
                logger.trace { "Will not broadcast packet ${packetToString(packet, packetCodec.getPacketVersion())} to peer $it due to peer packet version $peerPacketVersion" }
            }
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

    override fun getPeerPacketVersion(peerId: NodeRid): Long = nodePacketVersions.getOrDefault(peerId, 1)

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

    internal fun consumePacket(packet: ByteArray, peerId: NodeRid) {
        try {
            /**
             * Packet decoding should not be synchronized, so we can make
             * use of parallel processing in different threads
             */
            val packetVersion = getPacketVersion(peerId, packet)
            val decodedPacket = decodePacket(peerId, packet, packetVersion) ?: return
            synchronized(this) {
                if (logger.isTraceEnabled) {
                    withLoggingContext(
                            *baseLoggingContext,
                            SOURCE_NODE_TAG to peerId.toHex(),
                            TARGET_NODE_TAG to myPeerId,
                            MESSAGE_TYPE_TAG to decodedPacket::class.java.simpleName
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

    private fun decodePacket(peerId: NodeRid, packet: ByteArray, packetVersion: Long) =
            try {
                packetCodec.decodePacket(peerId.data, packet, packetVersion)
            } catch (e: UserMistake) {
                if (packetVersion > 1) {
                    // In some cases, our packet version has not been received quick enough by the other peer,
                    // so it will think we are on packet version 1. This will be resolved as soon as the version
                    // packet is received by the other node, but there is a small window when this might happen,
                    // and in that case we will try to decode with version 1 instead to verify that is the case.
                    // If it is version 1, we will discard it since we do not want to risk the version being used
                    // further up the call chain, e.g., starting the AppliedConfigSender when we should not.
                    try {
                        packetCodec.decodePacket(peerId.data, packet, 1)
                        logger.info { "Got exception when decoding receive packet from ${peerId.toHex()} with version $packetVersion. Retry with version 1 succeeded so discarding packet." }
                        null
                    } catch (e2: Exception) {
                        // Throw the original exception if there really is a problem with the verification and not a
                        // version problem.
                        throw e
                    }
                } else {
                    throw e
                }
            }

    private fun getPacketVersion(peerId: NodeRid, packet: ByteArray): Long {
        if (nodePacketVersions[peerId] == null) {
            if (packetCodec.isVersionPacket(packet)) {
                nodePacketVersions[peerId] = packetCodec.parseVersionPacket(packet)
                logger.info { "Got packet version ${nodePacketVersions[peerId]} from $peerId" }
            } else {
                // If we end up here, we did not get a version packet from the other node (yet),
                // and so node is probably legacy of version 1
                logger.info { "Did not receive version for peer $peerId. Will default to packet version 1." }
                nodePacketVersions[peerId] = 1
            }
        }
        return getPeerPacketVersion(peerId)
    }
}