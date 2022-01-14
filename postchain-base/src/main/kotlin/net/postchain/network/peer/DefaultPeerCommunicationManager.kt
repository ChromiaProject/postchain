// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.devtools.NameHelper.peerName
import net.postchain.network.common.ConnectionManager
import net.postchain.network.CommunicationManager
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import net.postchain.network.mastersub.subnode.SubConnectionManager

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

    private var inboundPackets = mutableListOf<Pair<NodeRid, PacketType>>()
    var connected = false

    /**
     * Main job during init() is to connect our chain using the [PeerConnectionManager].
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

    @Synchronized
    override fun getPackets(): MutableList<Pair<NodeRid, PacketType>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf()
        return currentQueue
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

        connectionManager.broadcastPacket(
                { packetEncoder.encodePacket(packet) },
                chainId
        )
    }

    /**
     * We need to handle random peer differently depending if we are in a subnode or if we are connected to peers
     *
     * TODO: Olle: should this be a class for "Sub Comm Mgr" instead of this runtime switch?
     *
     * @param packet is the data to send
     * @param amongPeers is the set of nodes acceptable to send to
     * @return a randomly picked peer from the give set that has an open connection, or "null" if none found.
     */
    override fun sendToRandomPeer(packet: PacketType, amongPeers: Set<NodeRid>): NodeRid? {
        var randomPeer: NodeRid? = null
        when (connectionManager) {
            is PeerConnectionManager -> {
                // 1. This is the case for "normal" nodes and master nodes
                val possiblePeers = connectionManager.getConnectedPeers(chainId).intersect(amongPeers)
                if (possiblePeers.isEmpty()) {
                    return null // We don't want to apply random to an empty list b/c throwing exception is too expensive.
                }
                randomPeer = possiblePeers.random()
                if (logger.isTraceEnabled) {
                    logger.trace("$processName: sendToRandomPeer($packet, ${peerName(randomPeer.toString())})")
                }
            }
            is SubConnectionManager -> {
                // 2. We are in a sub-node so we don't have access to the active peer connections (only the master does)
                // Therefore we get the cached connection data that we got from master.

                throw ProgrammerMistake("Not impl") //TODO: Olle:

                logger.debug {
                    "$processName: sendToRandomPeer($packet, ${peerName(randomPeer.toString())}) - sub node"
                }


            }
            else -> {
                throw ProgrammerMistake( "The connectionManager is not a Peer Conn Mgr nor a Sub Conn Mgr, " +
                            "giving up. $connectionManager."
                )
            }
        }
        return try {
            sendPacket(packet, randomPeer!!)
            randomPeer!!
        } catch (e: Exception) {
            logger.error("Could not send package to random peer: ${peerName(randomPeer.toString())} " +
                    "because: ${e.message}")
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
        try {
            /**
             * Packet decoding should not be synchronized so we can make
             * use of parallel processing in different threads
             */
            logger.trace {"Receiving a packet from peer: ${peerId.byteArray.toHex()}"}
            val decodedPacket = packetDecoder.decodePacket(peerId.byteArray, packet)
            synchronized(this) {
                logger.trace {"Successfully decoded the package, now adding it "}
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