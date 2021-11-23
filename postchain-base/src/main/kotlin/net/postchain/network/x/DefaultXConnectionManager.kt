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

open class DefaultXConnectionManager<PacketType>(
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
    override fun sendPacket(data: LazyPacket, chainId: Long, peerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        chain.connections[peerId]?.sendPacket(data)
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainId: Long) {
        // TODO: lazypacket might be computed multiple times
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        chain.connections.forEach { (_, conn) ->
            conn.sendPacket(data)
        }
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

    @Synchronized
    override fun onPeerConnected(connection: XPeerConnection): XPacketHandler? {
        val descriptor = connection.descriptor()
        logger.info {
            "${logger(descriptor)}: New ${descriptor.dir} connection: peer = ${peerName(descriptor.peerId)}" +
                    ", blockchainRid: ${descriptor.blockchainRid}"
        }

        val chainId = chainIdForBlockchainRid[descriptor.blockchainRid]
        if (chainId == null) {
            logger.warn("${logger(descriptor)}: onPeerConnected: chainId not found by blockchainRID = ${descriptor.blockchainRid}")
            connection.close()
            return null
        }
        val chain = chains[chainId]
        if (chain == null) {
            logger.warn("${logger(descriptor)}: onPeerConnected: Chain not found by chainID = $chainId / blockchainRID = ${descriptor.blockchainRid}. " +
                    "(This is expected if it happens after this chain was restarted).")
            connection.close()
            return null
        }

        return if (!chain.peerConfig.commConfiguration.networkNodes.isNodeBehavingWell(descriptor.peerId, System.currentTimeMillis())) {
            logger.debug { "${logger(descriptor)}: onPeerConnected: Peer not behaving well, so ignore: peer = ${peerName(descriptor.peerId)}" }
            null
        } else {
            val originalConn = chain.connections[descriptor.peerId]
            if (originalConn != null) {
                logger.debug { "${logger(descriptor)}: onPeerConnected: Peer already connected: peer = ${peerName(descriptor.peerId)}" }
                val isOriginalOutgoing = originalConn.descriptor().isOutgoing()
                if (peersConnectionStrategy.duplicateConnectionDetected(chainId, isOriginalOutgoing, descriptor.peerId)) {
                    disconnectChainPeer(chainId, descriptor.peerId)
                    chain.connections[descriptor.peerId] = connection
                    logger.debug {
                        "${logger(descriptor)}: onPeerConnected: Peer connected and replaced previous connection: peer = ${peerName(descriptor.peerId)}"
                    }
                    chain.peerConfig.packetHandler
                } else {
                    connection.close()
                    null
                }
            } else {
                chain.connections[descriptor.peerId] = connection
                logger.debug { "${logger(descriptor)}: onPeerConnected: Connection accepted: peer = ${peerName(descriptor.peerId)}" }
                peersConnectionStrategy.connectionEstablished(chainId, connection.descriptor().isOutgoing(), descriptor.peerId)
                chain.peerConfig.packetHandler
            }
        }
    }

    /**
     * We often don't know why we got a disconnect.
     * It could be because we did "disconnectChain()" ourselves, and for those cases we don't even have the BC is chain[].
     */
    @Synchronized
    override fun onPeerDisconnected(connection: XPeerConnection) {
        val descriptor = connection.descriptor()

        var chainId = chainIdForBlockchainRid[descriptor.blockchainRid]
        if (chainId == null) {
            val oldChainId = disconnectedChainIdForBlockchainRid[descriptor.blockchainRid]
            if (oldChainId != null) {
                // Ok, we are here because someone called "disconnectChain()"
                // and we lost the chainId, let's take the old one.
                chainId = oldChainId
            } else {
                logger.error("${loggingPrefix(descriptor)}: Peer disconnected: " +
                        "How can we never have seen chain: ${peerName(descriptor.peerId)}, " +
                        "direction: ${descriptor.dir}, " +
                        "blockchainRid = ${descriptor.blockchainRid} / chainId = $chainId.\")."
                )
                connection.close()
                return
            }
        }

        val chain = chains[chainId]
        if (chain == null) {
            // This is not an error
            logger.debug("${loggingPrefix(descriptor)}: Peer disconnected: chain structure gone, probably " +
                    " removed by disconnectChain(). peer: ${peerName(descriptor.peerId)}, " +
                    "direction: ${descriptor.dir}, blockchainRid = ${descriptor.blockchainRid} / chainId = $chainId.\").")
            connection.close()
            return
        }

        if (chain.connections[descriptor.peerId] == connection) {
            logger.debug(
                    "${loggingPrefix(descriptor)}: Peer disconnected: Removing peer: ${peerName(descriptor.peerId)}" +
                            ", direction: ${descriptor.dir} from blockchainRID = ${descriptor.blockchainRid} / chainID = $chainId."
            )
            // It's the connection we're using, so we have to remove it
            chain.connections.remove(descriptor.peerId)
        } else {
            // This is the normal case when a Netty connection fails immediately
        }
        connection.close()
        if (chain.connectAll) {
            peersConnectionStrategy.connectionLost(chainId!!, descriptor.peerId, descriptor.isOutgoing())
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