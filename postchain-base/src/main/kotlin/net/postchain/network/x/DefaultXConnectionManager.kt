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
import net.postchain.devtools.PeerNameHelper.peerName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import org.bitcoinj.core.Peer

class DefaultXConnectionManager<PacketType>(
        private val connectorFactory: XConnectorFactory<PacketType>,
        private val packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        private val packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        cryptoSystem: CryptoSystem
) : XConnectionManager, XConnectorEvents {

    companion object : KLogging()

    private var connector: XConnector<PacketType>? = null
    private lateinit var peersConnectionStrategy: PeersConnectionStrategy

    // Used by connection strategy, connector and loggers (to distinguish nodes in tests' logs).
    private lateinit var myPeerInfo: PeerInfo

    private class Chain(
            val peerConfig: XChainPeerConfiguration,
            val connectAll: Boolean) {
        val connections = mutableMapOf<XPeerID, XPeerConnection>()
    }

    private val chains: MutableMap<Long, Chain> = mutableMapOf()
    private val chainIDforBlockchainRID = mutableMapOf<BlockchainRid, Long>()
    private val disconnectedChainIDforBlockchainRID =
        mutableMapOf<BlockchainRid, Long>() // Needed for serious inconsistencies
    private var isShutDown = false

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

    private fun updateBridToChainIDCache(blockchainRid: BlockchainRid, peerConfig: XChainPeerConfiguration) {
        val foundChainId = chainIDforBlockchainRID[blockchainRid]
        if (foundChainId == null) {
            chainIDforBlockchainRID[blockchainRid] = peerConfig.chainID
        } else {
            if (foundChainId != peerConfig.chainID) {
                throw ProgrammerMistake("Chain ${peerConfig.blockchainRID} cannot be connected to ${peerConfig.chainID} is connected to a different chain: $foundChainId. ")
            }
        }
    }

    @Synchronized
    override fun connectChain(peerConfig: XChainPeerConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        logger.debug {
            "${loggingPrefix()}: Connecting chain: ${peerConfig.chainID}" +
                    ", blockchainRID: ${peerConfig.blockchainRID.toShortHex()}"
        }

        if (isShutDown) throw ProgrammerMistake("Already shut down")


        val chainID = peerConfig.chainID
        if (chainID in chains) {
            throw ProgrammerMistake("Chain is already connected ${chainID}")
        }
        updateBridToChainIDCache(peerConfig.blockchainRID, peerConfig)
        chains[peerConfig.chainID] = Chain(peerConfig, autoConnectAll)

        // We used to create the connector at object init. But a
        // problem with initiating the connector before connecting all chains
        // is that we might close legit incoming connections that are for blockchains
        // that haven't been connected yet.
        // During startup, It'd be better to create the connector once all
        // currently known chains have been connected.
        // This solution is getting us half-way. We solve the issue for the first
        // blockchain started, but not for subsequent ones.
        if (connector == null) {
            myPeerInfo = peerConfig.commConfiguration.myPeerInfo()
            peersConnectionStrategy = DefaultPeersConnectionStrategy(this, myPeerInfo.peerId())
            connector = connectorFactory.createConnector(
                    myPeerInfo,
                    packetDecoderFactory.create(peerConfig.commConfiguration),
                    this)
        }

        if (autoConnectAll) {
            val commConf = peerConfig.commConfiguration
            peersConnectionStrategy.connectAll(chainID, commConf.networkNodes.getPeerIds())
        }

        logger.debug { "${logger(peerConfig)}: Chain connected: ${peerConfig.chainID}" }
    }

    private fun connectorConnectPeer(peerConfig: XChainPeerConfiguration, peerId: XPeerID) {
        logger.info { "${logger(peerConfig)}: Connecting chain peer: chain = ${peerConfig.chainID}, peer = ${peerName(peerId)}" }

        val peerConnectionDescriptor = XPeerConnectionDescriptor(
                peerId,
                peerConfig.blockchainRID)

        val peerInfo = peerConfig.commConfiguration.resolvePeer(peerId.byteArray)
                ?: throw ProgrammerMistake("Peer ID not found: ${peerId.byteArray.toHex()}")
        if (peerInfo.peerId() != peerId) {
            // Have to add this check since I see strange things
            throw IllegalStateException("Peer id found in comm config not same as we looked for ${peerId.byteArray.toHex()} +" +
                    ", found: ${peerInfo.peerId().byteArray.toHex()} ")
        }

        val packetEncoder = packetEncoderFactory.create(
                peerConfig.commConfiguration,
                peerConfig.blockchainRID)

        connector?.connectPeer(peerConnectionDescriptor, peerInfo, packetEncoder)
    }

    @Synchronized
    override fun connectChainPeer(chainID: Long, peerID: XPeerID) {
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        if (peerID in chain.connections) {
            if (logger.isDebugEnabled) {
                logger.debug("${logger(chain.peerConfig)}: connectChainPeer() - already connected chain $chainID to peer: ${peerID.shortString()} so do nothing. ")
            }
        } else {
            connectorConnectPeer(chain.peerConfig, peerID)
        }
    }

    @Synchronized
    override fun isPeerConnected(chainID: Long, peerID: XPeerID): Boolean {
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        return peerID in chain.connections
    }

    @Synchronized
    override fun getConnectedPeers(chainID: Long): List<XPeerID> {
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        return chain.connections.keys.toList()
    }

    @Synchronized
    override fun sendPacket(data: LazyPacket, chainID: Long, peerID: XPeerID) {
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        chain.connections[peerID]?.sendPacket(data)
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainID: Long) {
        // TODO: lazypacket might be computed multiple times
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        chain.connections.forEach { (_, conn) ->
            conn.sendPacket(data)
        }
    }

    @Synchronized
    override fun disconnectChainPeer(chainID: Long, peerID: XPeerID) {
        val chain = chains[chainID] ?: throw ProgrammerMistake("Chain ID not found: $chainID")
        val conn = chain.connections[peerID]
        if (conn != null) {
            conn.close()
            chain.connections.remove(peerID)
        } else {
            logger.debug("${logger(chain.peerConfig)}: connectChainPeer() - cannot connect chain $chainID to peer: ${peerID.shortString()} b/c chain missing that connection. ")
        }
    }

    @Synchronized
    override fun disconnectChain(chainID: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting chain: $chainID" }

        // Remove the chain before closing connections so that we won't
        // reconnect in onPeerDisconnected()
        val chain = chains.remove(chainID)
        if (chain != null) {
            val old = chainIDforBlockchainRID.remove(chain.peerConfig.blockchainRID)
            if (old != null) {
                disconnectedChainIDforBlockchainRID[chain.peerConfig.blockchainRID] = old
            }
            chain.connections.forEach { (_, conn) ->
                conn.close()
            }
            chain.connections.clear()
            logger.debug { "${loggingPrefix()}: Chain disconnected: $chainID" }
        } else {
            logger.debug { "${loggingPrefix()}: Unknown chain: $chainID" }
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
                    ", blockchainRID: ${descriptor.blockchainRID}" +
                    ", (size of c4Brid: ${chainIDforBlockchainRID.size}, size of chains: ${chains.size}) "
        }

        // ----------------------
        // 1. See if we have enough data to proceed with this connection
        // (if not we shut it down)
        // ----------------------
        val chainID = chainIDforBlockchainRID[descriptor.blockchainRID]
            ?: when (descriptor.dir) {
                direction.INCOMING -> {
                    // Here we are forgiving, since it's ok to not know the Chain IID
                    val oldChainID = disconnectedChainIDforBlockchainRID[descriptor.blockchainRID]
                    if (oldChainID != null) {
                        logger.debug { "${logger(descriptor)}: onPeerConnected()  - Found chainIid: $oldChainID for " +
                                " ${descriptor.blockchainRID} in the backup." }
                        oldChainID
                    } else {
                        logger.info {"${logger(descriptor)}: onPeerConnected() - Chain ID not found by " +
                                " blockchainRID = ${descriptor.blockchainRID}" +
                                " (Could be due to 1) chain not started or 2) we really don't have it)"
                        }
                        connection.close()
                        return null
                    }
                }

                direction.OUTGOING -> {
                    logger.error { "${logger(descriptor)}: onPeerConnected() - We initiated this contact but lost "+
                            " the Chain ID for blockchainRID = ${descriptor.blockchainRID}." }
                    connection.close()
                    return null
                }
            }

        val chain = chains[chainID]
            ?: when (descriptor.dir) {
                direction.INCOMING -> {
                    logger.info("${logger(descriptor)}: onPeerConnected() - Chain not found by chainID = $chainID /" +
                                " blockchainRID = ${descriptor.blockchainRID}. " +
                                "(This is expected if it happens after this chain was restarted)."
                    )
                    connection.close()
                    return null
                }
                direction.OUTGOING -> {
                    logger.error {"${logger(descriptor)}: onPeerConnected() - We initiated this contact but lost " +
                                "the Chain for chainID = $chainID / blockchainRID = ${descriptor.blockchainRID}."
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
        val chainID: Long =  chainIDforBlockchainRID[descriptor.blockchainRID]
            ?: when (descriptor.dir) {
                direction.INCOMING -> {
                    val oldChainID = disconnectedChainIDforBlockchainRID[descriptor.blockchainRID]
                    if (oldChainID != null) {
                        logger.debug {"${logger(descriptor)}: onPeerDisconnected() - Had to get chainIid: " +
                                "$oldChainID from the backup." }
                        oldChainID
                    } else {
                        // One valid way we might end up here:
                        // Another node connects us about chain X
                        // We don't have chain X so we close the connection in "onPeerConnection(), and
                        // Netty will call the callback "onPeerDisconnected()", and now we are here.
                        logger.info { "${logger(descriptor)}: onPeerDisconnected() - Chain ID not found by " +
                                " blockchainRID = ${descriptor.blockchainRID}" +
                                " (Could be due to 1) chain not started or 2) we really don't have it)" }
                        connection.close()
                        return
                    }
                }
                direction.OUTGOING -> {
                    val oldChainID = disconnectedChainIDforBlockchainRID[descriptor.blockchainRID]
                    if (oldChainID != null) {
                        // If we disconnected, this is expected.
                        logger.debug { "${logger(descriptor)}: onPeerDisconnected() - Had to get chainIid: " +
                                    "$oldChainID from the backup."
                        }
                        oldChainID
                    } else {
                        logger.error( "${logger(descriptor)}: onPeerDisconnected() - How can we never have seen " +
                                    "chain: from peer: ${peerName(descriptor.peerId)} , direction: " +
                                    "${descriptor.dir}, blockchainRID = ${descriptor.blockchainRID}) . "
                        )
                        connection.close()
                        return
                    }
                }
        }

        val chain = chains[chainID]
        if (chain == null) {
            // This is not an error (we don't even have to check the direction)
            logger.debug { "${logger(descriptor)}: onPeerDisconnected() - chain structure gone, probably removed " +
                    "by disconnectChain(). peer: ${peerName(descriptor.peerId)}, direction: ${descriptor.dir}, " +
                    "blockchainRID = ${descriptor.blockchainRID} / chainID = $chainID.\") . "}
            connection.close()
            return
        }

        // ----------------------
        // 2. We have what we need to proceed with closing the connection
        // ----------------------
        if (chain.connections[descriptor.peerId] == connection) {
            logger.debug { "${logger(descriptor)}: onPeerDisconnected() - Peer disconnected: Removing peer: " +
                    "${ peerName(descriptor.peerId )}, direction: ${descriptor.dir} from " +
                    "blockchainRID = ${descriptor.blockchainRID} / chainID = $chainID." }
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
                .mapKeys { (id, chain) -> id to chain.peerConfig.blockchainRID.toHex() }
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
            myPeerInfo.peerId().toString(),
            blockchainRid
    ).toString()

    private fun logger(descriptor: XPeerConnectionDescriptor): String = descriptor.loggingPrefix(myPeerInfo.peerId())

    private fun logger(config: XChainPeerConfiguration): String = loggingPrefix(config.blockchainRID)
}