// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.core.NodeRid
import net.postchain.crypto.CryptoSystem
import net.postchain.devtools.NameHelper
import net.postchain.devtools.NameHelper.peerName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import net.postchain.network.common.*
import net.postchain.network.netty2.NettyPeerConnector
import net.postchain.debug.BlockchainProcessName

/**
 * Default implementation for "peer" based networks (which EBFT is).
 *
 * -----------------------------
 * Collection of connections
 * -----------------------------
 * Main job of this class is to provide a collection of all connections that are currently open.
 * The number of connections is, for a typical node cluster:
 *
 *   Nr of Conns = (nr of peers - 1) * (nr of chains).
 *
 * Example, a cluster of 10 peers where each peer runs 100 chains each, the total number of connections will be:
 *
 *   Nr of Conns = (10 -1) * (100) = 900
 *
 * Also, every replica that connects to a node will get a connection, so there will often be more that this.
 *
 * The reason for having this many connections is that we want to allow chain restarts with minimal disturbance for
 * other chains, so when a chain goes down we close and remove the connection for it, but don't touch the other
 * connections.
 *
 * -----------------------------
 * Blockchain RID <-> Chain IID
 * -----------------------------
 * One important aspect of this implementation is the idea of Blockchain RID <-> Chain IID translation.
 * When other nodes talk about a chain they must use the BC RID, since this name will be unique on the planet,
 * but when inside Postchain it is more convenient for us to use the Chain IID, since it is shorter.
 * (The Chain IID cannot be allowed to leak out from the node, since it is NOT unique outside the node)
 * Therefore we use a cache for translating back and forth between BC RID <-> Chain IID.
 *
 * @property PacketType is the type of packets that can be handled
 */
open class DefaultPeerConnectionManager<PacketType>(
        private val packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        private val packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        cryptoSystem: CryptoSystem
) : NetworkTopology, // Only "Peer" neworks need this
        PeerConnectionManager,  // Methods specific to the "X" connection part
        NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor> {

    companion object : KLogging()

    /**
     * A collection of all our connections (sorted and grouped by Chain IID).
     */
    private val chainsWithConnections =
            ChainsWithConnections<   // Too much type magic here, not sure if it can be reduced
                    PeerPacketHandler,
                    PeerConnection,
                    ChainWithPeerConnections
                    >()

    /**
     * A cache BC RID -> Chain IID
     */
    private val chainIdForBlockchainRid = mutableMapOf<BlockchainRid, Long>()
    private val disconnectedChainIdForBlockchainRid = mutableMapOf<BlockchainRid, Long>() // We keep history for debug n bkp.

    private var isShutDown = false

    private var connector: NodeConnector<PacketType, PeerConnectionDescriptor>? = null
    private lateinit var peersConnectionStrategy: PeersConnectionStrategy

    // Used by connection strategy, connector and loggers (to distinguish nodes in tests' logs).
    private lateinit var myPeerInfo: PeerInfo

    override fun shutdown() {
        connector?.shutdown()
        if (::peersConnectionStrategy.isInitialized) peersConnectionStrategy.shutdown()

        synchronized(this) {
            isShutDown = true
            chainsWithConnections.removeAllAndClose()
        }
    }

    /**
     * Update cache
     */
    private fun updateBridToChainIDCache(blockchainRid: BlockchainRid, chainIid: Long) {
        val foundChainId = chainIdForBlockchainRid[blockchainRid]
        if (foundChainId == null) {
            chainIdForBlockchainRid[blockchainRid] = chainIid
        } else {
            if (foundChainId != chainIid) {
                throw ProgrammerMistake("Chain cannot be connected to $chainIid is connected to a different chain: $foundChainId. ")
            }
        }
    }

    /**
     * Before connecting a [ChainWithConnections] we must do some preparations
     */
    @Synchronized
    fun beforeConnect(
            blockchainRid: BlockchainRid,
            chainWithConnections: ChainWithPeerConnections
    ) {
        if (isShutDown) throw ProgrammerMistake("Already shut down")

        if (chainsWithConnections.hasChain(chainWithConnections.getChainIid())) {
            throw ProgrammerMistake("Chain is already connected ${chainWithConnections.getChainIid()}")
        }
        updateBridToChainIDCache(blockchainRid, chainWithConnections.getChainIid())
        chainsWithConnections.add(chainWithConnections)
    }

    @Synchronized
    override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        logger.debug {
            "${loggingPrefix()}: Connecting chain: ${chainPeersConfig.chainId}" +
                    ", blockchainRID: ${chainPeersConfig.blockchainRid.toShortHex()}"
        }

        if (isShutDown) throw ProgrammerMistake("Already shut down")
        val chainID = chainPeersConfig.chainId
        val chainWithConnections = ChainWithPeerConnections(
                chainPeersConfig.chainId, chainPeersConfig, autoConnectAll
        )
        beforeConnect(chainPeersConfig.blockchainRid, chainWithConnections)

        // We used to create the connector at object init. But a
        // problem with initiating the connector before connecting all chains
        // is that we might close legit incoming connections that are for blockchains
        // that haven't been connected yet.
        // During startup, It'd be better to create the connector once all
        // currently known chains have been connected.
        // This solution is getting us half-way. We solve the issue for the first
        // blockchain started, but not for subsequent ones.
        if (connector == null) {
            myPeerInfo = chainPeersConfig.commConfiguration.myPeerInfo()
            peersConnectionStrategy = DefaultPeersConnectionStrategy(this, myPeerInfo.peerId())

            val packetDecoder = packetDecoderFactory.create(chainPeersConfig.commConfiguration)
            // We have already given away we are using Netty, so skipping the factory
            connector = NettyPeerConnector<PacketType>(this).apply {
                init(myPeerInfo, packetDecoder)
            }
        }

        if (autoConnectAll) {
            val commConf = chainPeersConfig.commConfiguration
            peersConnectionStrategy.connectAll(chainID, commConf.networkNodes.getPeerIds())
        }

        logger.debug { "${logger(chainPeersConfig)}: Chain connected: ${chainPeersConfig.chainId}" }
    }

    private fun connectorConnectPeer(chainPeersConfig: XChainPeersConfiguration, peerId: NodeRid) {
        logger.info(
                "${logger(chainPeersConfig)}: Connecting chain peer: chain = ${chainPeersConfig.chainId}, " +
                        "peer = ${peerName(peerId)}"
        )

        val descriptor = PeerConnectionDescriptor(
                chainPeersConfig.blockchainRid,
                peerId,
                ConnectionDirection.OUTGOING
        )

        val peerInfo = chainPeersConfig.commConfiguration.resolvePeer(peerId.byteArray)
                ?: throw ProgrammerMistake("Peer ID not found: ${peerId.byteArray.toHex()}")
        if (peerInfo.peerId() != peerId) {
            // Have to add this check since I see strange things
            throw ProgrammerMistake(
                    "Peer id found in comm config not same as we looked for" +
                            " ${peerId.byteArray.toHex()}, found: ${peerInfo.peerId().byteArray.toHex()} "
            )
        }

        val packetEncoder = packetEncoderFactory.create(
                chainPeersConfig.commConfiguration,
                chainPeersConfig.blockchainRid
        )

        connector?.connectNode(descriptor, peerInfo, packetEncoder)
    }

    @Synchronized
    override fun connectChainPeer(chainId: Long, peerId: NodeRid) {
        val chain = chainsWithConnections.getOrThrow(chainId)
        if (chain.isConnected(peerId)) {
            logger.debug {
                "${logger(chain.peerConfig)}: connectChainPeer() - already connected chain $chainId " +
                        "to peer: ${peerId.shortString()} so do nothing. "
            }
        } else {
            connectorConnectPeer(chain.peerConfig, peerId)
        }
    }

    @Synchronized
    override fun isPeerConnected(chainId: Long, peerId: NodeRid): Boolean {
        return chainsWithConnections.getNodeConnection(chainId, peerId) != null
    }

    @Synchronized
    override fun sendPacket(data: LazyPacket, chainId: Long, nodeRid: NodeRid) {
        chainsWithConnections.getNodeConnection(chainId, nodeRid)?.sendPacket(data)
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainId: Long) {
        val chain = chainsWithConnections.getOrThrow(chainId)
        for (conn in chain.getAllConnections()) {
            conn.sendPacket(data)
        }
    }

    @Synchronized
    override fun getConnectedNodes(chainId: Long): List<NodeRid> {
        val chain = chainsWithConnections.get(chainId)
        return chain?.getAllNodes() ?: emptyList()
    }

    @Synchronized
    override fun disconnectChainPeer(chainId: Long, peerId: NodeRid) {
        val chain = chainsWithConnections.getOrThrow(chainId)
        val conn = chain.getConnection(peerId)
        if (conn != null) {
            chain.removeAndCloseConnection(peerId)
        } else {
            logger.debug(
                    "${logger(chain.peerConfig)}: connectChainPeer() - cannot connect chain $chainId " +
                            "to peer: ${peerId.shortString()} b/c chain missing that connection. "
            )
        }
    }

    @Synchronized
    override fun disconnectChain(loggingPrefix: () -> String, chainId: Long) {
        logger.debug { "${loggingPrefix()}: Disconnecting chain: $chainId" }

        // Remove the chain before closing connections so that we won't
        // reconnect in onPeerDisconnected()
        val chain = chainsWithConnections.remove(chainId)
        if (chain != null) {
            val old = chainIdForBlockchainRid.remove(chain.peerConfig.blockchainRid)
            if (old != null) {
                disconnectedChainIdForBlockchainRid[chain.peerConfig.blockchainRid] = old
            }
            chain.closeConnections()
            logger.debug { "${loggingPrefix()}: Chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Unknown chain: $chainId" }
        }
    }

    /**
     * We can get this callback in two ways. Either:
     *    a) OUTGOING: we connected the other part and got accepted or
     *    b) INCOMING: they connected us
     * For case b) we might not know the chain they want to connect about, and this is ok. We shouldn't report it.
     *
     * Implementation:
     *
     * 1. Initial validation: Try to find the Chain we need from the "descriptor" info
     * 2. Use the "connection": We have what we need to proceed, now deal with the connection itself
     */
    @Synchronized
    override fun onNodeConnected(connection: PeerConnection): PeerPacketHandler? {
        val descriptor = connection.descriptor()
        logger.info(
                "${logger(descriptor)}: onPeerConnected() - New ${descriptor.dir} connection: peer = " +
                        "${peerName(descriptor.nodeId)}, blockchainRID: ${descriptor.blockchainRid}, " +
                        "${chainsWithConnections.getStats()}"
        )

        // Find the connected Chain IID (Will report any error it finds)
        val chainID = getChainIdOnConnected(descriptor, connection) ?: return null
        // Find the connected chain (Will report any error it finds)
        val chain = getChainOnConnected(chainID, descriptor, connection) ?: return null

        // Find the [PeerPacketHandler]
        return if (!chain.isNodeBehavingWell(descriptor.nodeId)) {
            logger.debug {
                "${logger(descriptor)}: onPeerConnected() - Peer not behaving well, so ignore: " +
                        " peer = ${peerName(descriptor.nodeId)}"
            }
            null
        } else {
            val originalConn = chain.getConnection(descriptor.nodeId)
            if (originalConn != null) {
                logger.debug {
                    "${logger(descriptor)}: onPeerConnected() - Peer already connected: peer = ${peerName(descriptor.nodeId)}"
                }
                val isOriginalOutgoing = originalConn.descriptor().isOutgoing()
                if (peersConnectionStrategy.duplicateConnectionDetected(
                                chainID,
                                isOriginalOutgoing,
                                descriptor.nodeId
                        )
                ) {
                    disconnectChainPeer(chainID, descriptor.nodeId)
                    chain.setConnection(descriptor.nodeId, connection)
                    logger.debug {
                        "${logger(descriptor)}: onPeerConnected() - Peer connected and replaced previous " +
                                " connection: peer = ${peerName(descriptor.nodeId)}"
                    }
                    chain.getPacketHandler()
                } else {
                    connection.close()
                    null
                }
            } else {
                chain.setConnection(descriptor.nodeId, connection)
                logger.debug {
                    "${logger(descriptor)}: onPeerConnected() - Connection accepted: " +
                            "peer = ${peerName(descriptor.nodeId)}"
                }
                peersConnectionStrategy.connectionEstablished(
                        chainID,
                        connection.descriptor().isOutgoing(),
                        descriptor.nodeId
                )
                chain.getPacketHandler()
            }
        }
    }

    /**
     * We often don't know why we got a disconnect callback. These are some valid reasons:
     *    a) we did "disconnectChain()" ourselves b/c we need to restart the chain, or
     *    b) we did close() on the connection b/c the other side sent us a chain we don't know, or
     *    c) the node on the other side disconnected us.
     *
     * For case a) and b) we don't even have the BC in chain[], so we shouldn't worry about removing it.
     *
     * Implementation:
     *
     * 1. Initial validation: Try to find the Chain we need from the "descriptor" info
     * 2. Close the "connection": We have what we need to proceed, now do the cleanup
     */
    @Synchronized
    override fun onNodeDisconnected(connection: PeerConnection) {
        val descriptor = connection.descriptor()

        // Find the disconnected Chain IID
        var chainID: Long? = getChainIdOnDisconnected(descriptor)
        if (chainID == null) {
            connection.close()
            return
        }

        // Find the disconnected chain
        val chain = chainsWithConnections.get(chainID)
        if (chain == null) {
            // This is not an error (we don't even have to check the direction)
            logger.debug {
                "${logger(descriptor)}: onPeerDisconnected() - chain structure gone, probably removed " +
                        "by disconnectChain(). peer: ${peerName(descriptor.nodeId)}, direction: ${descriptor.dir}, " +
                        "blockchainRID = ${descriptor.blockchainRid} / chainID = $chainID.\") . "
            }
            connection.close()
            return
        }

        // Do the cleanup
        if (chain.getConnection(descriptor.nodeId) == connection) {
            logger.debug {
                "${logger(descriptor)}: onPeerDisconnected() - Peer disconnected: Removing peer: " +
                        "${peerName(descriptor.nodeId)}, direction: ${descriptor.dir} from " +
                        "blockchainRID = ${descriptor.blockchainRid} / chainID = $chainID."
            }
            // It's the connection we're using, so we have to remove (and close) it
            chain.removeAndCloseConnection(descriptor.nodeId)
        } else {
            // This is the normal case when a Netty connection fails immediately
            connection.close()
        }
        if (chain.shouldConnectAll()) {
            peersConnectionStrategy.connectionLost(chainID, descriptor.nodeId, descriptor.isOutgoing())
        }
    }

    /**
     * Maybe this is too complex, but IMO we do want to discover the error if we lose these IDs.
     *
     * @param descriptor
     * @param connection
     * @return the Chain IID if found
     */
    private fun getChainIdOnConnected(
            descriptor: PeerConnectionDescriptor,
            connection: PeerConnection,
    ): Long? {
        return chainIdForBlockchainRid[descriptor.blockchainRid]
                ?: when (descriptor.dir) {
                    ConnectionDirection.INCOMING -> {
                        // Here we are forgiving, since it's ok to not know the Chain IID
                        val oldChainID = disconnectedChainIdForBlockchainRid[descriptor.blockchainRid]
                        if (oldChainID != null) {
                            logger.debug {
                                "${logger(descriptor)}: getChainIdOnConnected()  - Found chainIid: $oldChainID for " +
                                        " ${descriptor.blockchainRid} in the backup."
                            }
                            oldChainID
                        } else {
                            logger.info(
                                    "${logger(descriptor)}: getChainIdOnConnected() - Chain ID not found by " +
                                            " blockchainRID = ${descriptor.blockchainRid}" +
                                            " (Could be due to 1) chain not started or 2) we really don't have it)"
                            )

                            connection.close()
                            return null
                        }
                    }
                    ConnectionDirection.OUTGOING -> {
                        logger.error(
                                "${logger(descriptor)}: getChainIdOnConnected() - We initiated this contact but lost " +
                                        " the Chain ID for blockchainRID = ${descriptor.blockchainRid}."
                        )
                        connection.close()
                        return null
                    }
                    null -> {
                        logger.warn(
                                "${logger(descriptor)}: getChainIdOnConnected() - Chain ID not found by " +
                                        " blockchainRID = ${descriptor.blockchainRid} and we don't know the connection's" +
                                        " direction."
                        )
                        connection.close()
                        return null
                    }
                }
    }

    /**
     * Maybe this is too complex, but IMO we want detail logs when missing chain
     */
    private fun getChainOnConnected(
            chainIid: Long,
            descriptor: PeerConnectionDescriptor,
            connection: PeerConnection,
    ): ChainWithPeerConnections? {
        return chainsWithConnections.get(chainIid)
                ?: when (descriptor.dir) {
                    ConnectionDirection.INCOMING -> {
                        logger.info(
                                "${logger(descriptor)}: getChainOnConnected() - Chain not found by chainID = $chainIid /" +
                                        " blockchainRID = ${descriptor.blockchainRid}. " +
                                        "(This is expected if it happens after this chain was restarted)."
                        )
                        connection.close()
                        return null
                    }
                    ConnectionDirection.OUTGOING -> {
                        logger.error(
                                "${logger(descriptor)}: getChainOnConnected() - We initiated this contact but lost " +
                                        "the Chain for chainID = $chainIid / blockchainRID = ${descriptor.blockchainRid}."
                        )
                        connection.close()
                        return null
                    }
                }
    }

    /**
     * Maybe this is too complex, but IMO we do want to discover the error if we lose these IDs.
     *
     * @param descriptor
     * @return the Chain IID if found
     */
    private fun getChainIdOnDisconnected(
            descriptor: PeerConnectionDescriptor
    ): Long? {
        return chainIdForBlockchainRid[descriptor.blockchainRid]
                ?: disconnectedChainIdForBlockchainRid[descriptor.blockchainRid] // If we disconnected ourselves it is expected to find the chain IID here (since we cleared the cache)
                ?: when (descriptor.dir) {
                    ConnectionDirection.INCOMING -> {
                        // Example: One valid way we might end up here:
                        // 1. Another node connects us about chain X, and
                        // 2. we don't have chain X so we close the connection in "onPeerConnection(), and
                        // 3. Netty will make the callback "onPeerDisconnected()", and now we are here.
                        logger.info(
                                "${logger(descriptor)}: getChainIdOnDisconnected() - Chain ID not found by " +
                                        " blockchainRID = ${descriptor.blockchainRid}" +
                                        " (Could be due to 1) chain not started or 2) we really don't have it)"
                        )
                        null
                    }
                    ConnectionDirection.OUTGOING -> {
                        // Should never happen
                        logger.error(
                                "${logger(descriptor)}: getChainIdOnDisconnected() - How can we never have seen " +
                                        "chain: from peer: ${NameHelper.peerName(descriptor.nodeId)} , direction: " +
                                        "${descriptor.dir}, blockchainRID = ${descriptor.blockchainRid}) . "
                        )
                        null
                    }
                    null -> {
                        logger.warn(
                                "${logger(descriptor)}: getChainIdOnDisconnected() - Chain ID not found by " +
                                        " blockchainRID = ${descriptor.blockchainRid}" +
                                        " (and we don't know the direction)"
                        )
                        null
                    }
                }
    }


    /**
     * [NetworkTopology] impl
     */
    override fun getNodesTopology(): Map<String, Map<String, String>> {
        return chainsWithConnections.getNodesTopology()
    }

    override fun getNodesTopology(chainIid: Long): Map<NodeRid, String> {
        return chainsWithConnections.getNodesTopology(chainIid)
    }

    // -------------
    // Logging
    // -------------

    private fun loggingPrefix(blockchainRid: BlockchainRid): String = BlockchainProcessName(
            myPeerInfo.peerId().toString(), blockchainRid
    ).toString()

    private fun loggingPrefix(descriptor: PeerConnectionDescriptor): String {
        return "${myPeerInfo.peerId()} ${descriptor.loggingPrefix()}"
    }

    private fun logger(descriptor: PeerConnectionDescriptor): String =
            "${myPeerInfo.peerId()}, ${descriptor.loggingPrefix()}"

    private fun logger(config: XChainPeersConfiguration): String = loggingPrefix(config.blockchainRid)
}