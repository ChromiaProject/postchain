// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.toHex
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.debug.BlockchainProcessName
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
        private val packetDecoder: XPacketDecoder<PacketType>,
        protected val processName: BlockchainProcessName
) : CommunicationManager<PacketType> {

    companion object : KLogging()

    private val inboundPackets = MutableSharedFlow<Pair<NodeRid, PacketType>>(0, 1_000, BufferOverflow.DROP_LATEST)
    override val messages = inboundPackets.asSharedFlow()
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
        connectionManager.connectChain(peerConfig, true) { processName.toString() }
        connected = true
    }

    override fun sendPacket(packet: PacketType, recipient: NodeRid) {
        logger.trace { "$processName: sendPacket($packet, ${peerName(recipient.toString())})" }

        require(NodeRid(config.pubKey) != recipient) {
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

        val lazyPacket by lazy { packetEncoder.encodePacket(packet) }
        connectionManager.broadcastPacket(
                { lazyPacket },
                chainId
        )
    }

    /**
     * Sends the packet to a peer selected by random.
     *
     * @param packet is the data to send
     * @param amongPeers is the set of nodes acceptable to send to
     * @return a randomly picked peer from the give set that has an open connection, or "null" if none found.
     */
    override fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): NodeRid? {
        var peer: NodeRid? = null
        return try {
            val possiblePeers = connectionManager.getConnectedNodes(chainId).intersect(amongPeers)
            if (possiblePeers.isEmpty()) {
                return null // We don't want to apply random to an empty list b/c throwing exception is too expensive.
            }
            peer = possiblePeers.random()
            if (logger.isTraceEnabled) {
                logger.trace("$processName: sendToRandomPeer($packet, ${peerName(peer.toString())})")
            }
            sendPacket(packet, peer)
            peer
        } catch (e: Exception) {
            logger.error("Could not send package to random peer: ${peer?.let { peerName(it) }} because: ${e.message}", e)
            null
        }
    }

    @Synchronized
    override fun shutdown() {
        if (!connected) return
        val prefixFun: () -> String = { processName.toString() }
        connectionManager.disconnectChain(prefixFun, chainId)
        connected = false
    }


    private fun consumePacket(packet: ByteArray, peerId: NodeRid) {
        runBlocking {
            try {
                logger.trace { "Receiving a packet from peer: ${peerId.byteArray.toHex()}" }
                val decodedPacket = packetDecoder.decodePacket(peerId.byteArray, packet)
                logger.trace { "Successfully decoded the package, now adding it " }
                inboundPackets.emit(peerId to decodedPacket)
            } catch (e: BadDataMistake) {
                if (e.type == BadDataType.BAD_MESSAGE) {
                    logger.info("Bad message received from peer ${peerId}: ${e.message}")
                } else {
                    logger.error("Error when receiving message from peer $peerId", e)
                }
            }
        }
    }
}