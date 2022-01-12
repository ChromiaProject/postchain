// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import mu.KLogging
import net.postchain.base.CryptoSystem
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.toHex
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake
import net.postchain.debug.BlockchainProcessName
import net.postchain.devtools.NameHelper.peerName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory

private const val NETTY_TIMEOUT = 20L

open class DefaultXConnectionManager<PacketType>(

        private val connectorFactory: XConnectorFactory<PacketType>,
        private val packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        private val packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        cryptoSystem: CryptoSystem
) : XConnectionManager, XConnectorEvents {

    companion object : KLogging()

    private val MAX_RETRIES = 3
    private var connector: XConnector<PacketType>? = null
    private lateinit var peersConnectionStrategy: PeersConnectionStrategy

    // Used by connection strategy, connector and loggers (to distinguish nodes in tests' logs).
    private lateinit var myPeerInfo: PeerInfo

    private class Chain(
            val peerConfig: XChainPeersConfiguration,
            val connectAll: Boolean) {
        val connections = mutableMapOf<XPeerID, XPeerConnection>()
    }

    private val chains: MutableMap<Long, Chain> = mutableMapOf()
    private val chainIdForBlockchainRid = mutableMapOf<BlockchainRid, Long>()
    private val disconnectedChainIdForBlockchainRid = mutableMapOf<BlockchainRid, Long>() // Needed for serious inconsistencies
    protected var isShutDown = false

    override fun shutdown() {
        connector?.shutdown()
        if (::peersConnectionStrategy.isInitialized) peersConnectionStrategy.shutdown()
        synchronized(this) {
            isShutDown = true
            chains.forEach { (_, chain) ->
                chain.connections.forEach { (_, conn) -> conn.close() }
            }
            chains.clear()
        }
    }

    private fun updateBridToChainIDCache(blockchainRid: BlockchainRid, peerConfig: XChainPeersConfiguration) {
        val foundChainId = chainIdForBlockchainRid[blockchainRid]
        if (foundChainId == null) {
            chainIdForBlockchainRid[blockchainRid] = peerConfig.chainId
        } else {
            if (foundChainId != peerConfig.chainId) {
                throw ProgrammerMistake("Chain ${peerConfig.blockchainRid} cannot be connected to ${peerConfig.chainId} is connected to a different chain: $foundChainId. ")
            }
        }
    }

    @Synchronized
    override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        logger.debug {
            "${loggingPrefix()}: Connecting chain: ${chainPeersConfig.chainId}" +
                    ", blockchainRID: ${chainPeersConfig.blockchainRid.toShortHex()}"
        }

        if (isShutDown) throw ProgrammerMistake("Already shut down")
        val chainID = chainPeersConfig.chainId
        if (chainID in chains) {
            throw ProgrammerMistake("Chain is already connected ${chainID}")
        }
        updateBridToChainIDCache(chainPeersConfig.blockchainRid, chainPeersConfig)
        chains[chainPeersConfig.chainId] = Chain(chainPeersConfig, autoConnectAll)

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
            connector = connectorFactory.createConnector(
                    myPeerInfo,
                    packetDecoderFactory.create(chainPeersConfig.commConfiguration),
                    this
            )
        }

        if (autoConnectAll) {
            val commConf = chainPeersConfig.commConfiguration
            peersConnectionStrategy.connectAll(chainID, commConf.networkNodes.getPeerIds())
        }

        logger.debug { "${logger(chainPeersConfig)}: Chain connected: ${chainPeersConfig.chainId}" }
    }

    private fun connectorConnectPeer(chainPeersConfig: XChainPeersConfiguration, peerId: XPeerID) {
        logger.info { "${logger(chainPeersConfig)}: Connecting chain peer: chain = ${chainPeersConfig.chainId}, peer = ${peerName(peerId)}" }

        val peerConnectionDescriptor = XPeerConnectionDescriptor(
                peerId,
                chainPeersConfig.blockchainRid
        )

        val peerInfo = chainPeersConfig.commConfiguration.resolvePeer(peerId.byteArray)
                ?: throw ProgrammerMistake("Peer ID not found: ${peerId.byteArray.toHex()}")
        if (peerInfo.peerId() != peerId) {
            // Have to add this check since I see strange things
            throw IllegalStateException("Peer id found in comm config not same as we looked for ${peerId.byteArray.toHex()}" +
                    ", found: ${peerInfo.peerId().byteArray.toHex()} ")
        }

        val packetEncoder = packetEncoderFactory.create(
                chainPeersConfig.commConfiguration,
                chainPeersConfig.blockchainRid
        )

        connector?.connectPeer(peerConnectionDescriptor, peerInfo, packetEncoder)
    }

    @Synchronized
    override fun connectChainPeer(chainId: Long, peerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        if (peerId in chain.connections) {
            if (logger.isDebugEnabled) {
                logger.debug("${logger(chain.peerConfig)}: connectChainPeer() - already connected chain $chainId " +
                        "to peer: ${peerId.shortString()} so do nothing. ")
            }
        } else {
            connectorConnectPeer(chain.peerConfig, peerId)
        }
    }

    @Synchronized
    override fun isPeerConnected(chainId: Long, peerId: XPeerID): Boolean {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        return peerId in chain.connections
    }

    @Synchronized
    override fun getConnectedPeers(chainId: Long): List<XPeerID> {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        return chain.connections.keys.toList()
    }

    @Synchronized
    override fun sendPacket(data: LazyPacket, chainID: Long, peerID: XPeerID) {
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        val conn = chain.connections[peerID]
        if (conn != null) {
            sendPacketWithRetries(conn, data, peerID)
        }
    }

    private fun sendPacketWithRetries(conn: XPeerConnection, data: LazyPacket, peerID: XPeerID?) {
        var retries = 0
        while (retries < MAX_RETRIES && !conn.sendPacket(data)) {
            logger.error { "sendPacket() - Failed to send packet to peer ${peerID?.shortString()}. Retrying..." }
            Thread.sleep(NETTY_TIMEOUT)
            retries++
        }
        if (retries == MAX_RETRIES) logger.error { "sendPacket() - Could not recover from connection... channel inactive." }
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainId: Long) {
        // TODO: lazypacket might be computed multiple times
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        val failedConnections = mutableListOf<XPeerConnection>()
        chain.connections.forEach { (peerId, conn) ->
            if (!conn.sendPacket(data)) {
                logger.warn("broadcastPacket() - Failed to broadcast packet to ${peerId.shortString()}. ${failedConnections.size}");
                failedConnections.add(conn)
            }
        }
        failedConnections.forEach { sendPacketWithRetries(it, data, null) }
    }

    @Synchronized
    override fun disconnectChainPeer(chainId: Long, peerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        val conn = chain.connections[peerId]
        if (conn != null) {
            conn.close()
            chain.connections.remove(peerId)
        } else {
            logger.debug("${logger(chain.peerConfig)}: connectChainPeer() - cannot connect chain $chainId " +
                    "to peer: ${peerId.shortString()} b/c chain missing that connection. ")
        }
    }

    @Synchronized
    override fun disconnectChain(chainId: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting chain: $chainId" }

        // Remove the chain before closing connections so that we won't
        // reconnect in onPeerDisconnected()
        val chain = chains.remove(chainId)
        if (chain != null) {
            val old = chainIdForBlockchainRid.remove(chain.peerConfig.blockchainRid)
            if (old != null) {
                disconnectedChainIdForBlockchainRid[chain.peerConfig.blockchainRid] = old
            }
            chain.connections.forEach { (_, conn) ->
                conn.close()
            }
            chain.connections.clear()
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
    override fun onPeerConnected(connection: XPeerConnection): XPacketHandler? {
        val descriptor = connection.descriptor()
        logger.info {
            "${logger(descriptor)}: New ${descriptor.dir} connection: peer = ${peerName(descriptor.peerId)}" +
                    ", blockchainRID: ${descriptor.blockchainRid}" +
                    ", (size of c4Brid: ${ chainIdForBlockchainRid.size}, size of chains: ${chains.size}) "
        }

        // ----------------------
        // 1. See if we have enough data to proceed with this connection
        // (if not we shut it down)
        // ----------------------
        val chainID = chainIdForBlockchainRid[descriptor.blockchainRid]
            ?: when (descriptor.dir) {
                Direction.INCOMING -> {
                    // Here we are forgiving, since it's ok to not know the Chain IID
                    val oldChainID = disconnectedChainIdForBlockchainRid[descriptor.blockchainRid]
                    if (oldChainID != null) {
                        logger.debug { "${logger(descriptor)}: onPeerConnected()  - Found chainIid: $oldChainID for " +
                                " ${descriptor.blockchainRid} in the backup." }
                        oldChainID
                    } else {
                        logger.info {"${logger(descriptor)}: onPeerConnected() - Chain ID not found by " +
                                " blockchainRID = ${descriptor.blockchainRid}" +
                                " (Could be due to 1) chain not started or 2) we really don't have it)"
                        }
                        connection.close()
                        return null
                    }
                }

                Direction.OUTGOING -> {
                    logger.error { "${logger(descriptor)}: onPeerConnected() - We initiated this contact but lost "+
                            " the Chain ID for blockchainRID = ${descriptor.blockchainRid}." }
                    connection.close()
                    return null
                }
            }

        val chain = chains[chainID]
            ?: when (descriptor.dir) {
                Direction.INCOMING -> {
                    logger.info("${logger(descriptor)}: onPeerConnected() - Chain not found by chainID = $chainID /" +
                                " blockchainRID = ${descriptor.blockchainRid}. " +
                                "(This is expected if it happens after this chain was restarted)."
                    )
                    connection.close()
                    return null
                }
                Direction.OUTGOING -> {
                    logger.error {"${logger(descriptor)}: onPeerConnected() - We initiated this contact but lost " +
                                "the Chain for chainID = $chainID / blockchainRID = ${descriptor.blockchainRid}."
                    }
                    connection.close()
                    return null
                }
            }

        // ----------------------
        // 2. We have what we need to proceed
        // ----------------------
        return if (!chain.peerConfig.commConfiguration.networkNodes.isNodeBehavingWell(descriptor.peerId, System.currentTimeMillis())) {
            logger.debug { "${logger(descriptor)}: onPeerConnected() - Peer not behaving well, so ignore: " +
                    " peer = ${peerName(descriptor.peerId)}" }
            null
        } else {
            val originalConn = chain.connections[descriptor.peerId]
            if (originalConn != null) {
                logger.debug { "${logger(descriptor)}: onPeerConnected() - Peer already connected: peer = ${peerName(descriptor.peerId)}" }
                val isOriginalOutgoing = originalConn.descriptor().isOutgoing()
                if (peersConnectionStrategy.duplicateConnectionDetected(chainID, isOriginalOutgoing, descriptor.peerId)) {
                    disconnectChainPeer(chainID, descriptor.peerId)
                    chain.connections[descriptor.peerId] = connection
                    logger.debug { "${logger(descriptor)}: onPeerConnected() - Peer connected and replaced previous " +
                            " connection: peer = ${peerName(descriptor.peerId)}" }
                    chain.peerConfig.packetHandler
                } else {
                    connection.close()
                    null
                }
            } else {
                chain.connections[descriptor.peerId] = connection
                logger.debug { "${logger(descriptor)}: onPeerConnected() - Connection accepted: " +
                        "peer = ${peerName(descriptor.peerId)}" }
                peersConnectionStrategy.connectionEstablished(chainID, connection.descriptor().isOutgoing(), descriptor.peerId)
                chain.peerConfig.packetHandler
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
    override fun onPeerDisconnected(connection: XPeerConnection) {
        val descriptor = connection.descriptor()

        // ----------------------
        // 1. See if we have enough data to proceed with this connection
        // ----------------------
        var chainID: Long? = chainIdForBlockchainRid[descriptor.blockchainRid]
        if (chainID == null) {
            val oldChainID = disconnectedChainIdForBlockchainRid[descriptor.blockchainRid]
            if (oldChainID != null) {
                // If we disconnected ourselves this is expected (since we cleared the cache)
                chainID = oldChainID
            } else {
                when (descriptor.dir) {
                    Direction.INCOMING -> {
                        // Example: One valid way we might end up here:
                        // 1. Another node connects us about chain X, and
                        // 2. we don't have chain X so we close the connection in "onPeerConnection(), and
                        // 3. Netty will make the callback "onPeerDisconnected()", and now we are here.
                        logger.info { "${logger(descriptor)}: onPeerDisconnected() - Chain ID not found by " +
                                    " blockchainRID = ${descriptor.blockchainRid}" +
                                    " (Could be due to 1) chain not started or 2) we really don't have it)"
                        }
                    }
                    Direction.OUTGOING -> {
                        // Should never happen
                        logger.error( "${logger(descriptor)}: onPeerDisconnected() - How can we never have seen " +
                                    "chain: from peer: ${peerName(descriptor.peerId)} , direction: " +
                                    "${descriptor.dir}, blockchainRID = ${descriptor.blockchainRid}) . "
                        )
                    }
                }
                connection.close()
                return
            }
        }

        val chain = chains[chainID]
        if (chain == null) {
            // This is not an error (we don't even have to check the direction)
            logger.debug { "${logger(descriptor)}: onPeerDisconnected() - chain structure gone, probably removed " +
                    "by disconnectChain(). peer: ${peerName(descriptor.peerId)}, direction: ${descriptor.dir}, " +
                    "blockchainRID = ${descriptor.blockchainRid} / chainID = $chainID.\") . "}
            connection.close()
            return
        }

        // ----------------------
        // 2. We have what we need to proceed with closing the connection
        // ----------------------
        if (chain.connections[descriptor.peerId] == connection) {
            logger.debug { "${logger(descriptor)}: onPeerDisconnected() - Peer disconnected: Removing peer: " +
                    "${ peerName(descriptor.peerId )}, direction: ${descriptor.dir} from " +
                    "blockchainRID = ${descriptor.blockchainRid} / chainID = $chainID." }
            // It's the connection we're using, so we have to remove it
            chain.connections.remove(descriptor.peerId)
        } else {
            // This is the normal case when a Netty connection fails immediately
        }
        connection.close()
        if (chain.connectAll) {
            peersConnectionStrategy.connectionLost(chainID, descriptor.peerId, descriptor.isOutgoing())
        }
    }

    override fun getPeersTopology(): Map<String, Map<String, String>> {
        return chains
                .mapKeys { (id, chain) -> id to chain.peerConfig.blockchainRid.toHex() }
                .mapValues { (idToRid, _) -> getPeersTopology(idToRid.first).mapKeys { (k, _) -> k.toString() } }
                .mapKeys { (idToRid, _) -> idToRid.second }
    }

    override fun getPeersTopology(chainID: Long): Map<XPeerID, String> {
        return chains[chainID]
                ?.connections
                ?.mapValues { connection ->
                    (if (connection.value.descriptor().isOutgoing()) "c-s" else "s-c") + ", " + connection.value.remoteAddress()
                }
                ?: emptyMap()
    }

    private fun loggingPrefix(blockchainRid: BlockchainRid): String = BlockchainProcessName(
            myPeerInfo.peerId().toString(), blockchainRid
    ).toString()

    private fun loggingPrefix(descriptor: XPeerConnectionDescriptor): String {
        return descriptor.loggingPrefix(myPeerInfo.peerId())
    }

    private fun logger(descriptor: XPeerConnectionDescriptor): String = descriptor.loggingPrefix(myPeerInfo.peerId())

    private fun logger(config: XChainPeersConfiguration): String = loggingPrefix(config.blockchainRid)
}