// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.peerId
import net.postchain.common.toHex
import net.postchain.core.ProgrammerMistake
import net.postchain.core.byteArrayKeyOf
import net.postchain.debug.BlockchainProcessName
import net.postchain.devtools.NameHelper.peerName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory

open class DefaultXConnectionManager<PacketType>(
        connectorFactory: XConnectorFactory<PacketType>,
        val peerCommConfiguration: PeerCommConfiguration,
        private val packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        private val packetDecoderFactory: XPacketDecoderFactory<PacketType>
) : XConnectionManager, XConnectorEvents {

    private val connector = connectorFactory.createConnector(
            peerCommConfiguration.myPeerInfo(),
            packetDecoderFactory.create(peerCommConfiguration),
            this) // TODO: [POS-129]: ?

    companion object : KLogging()

    protected class Chain(
            val peerConfig: XChainPeersConfiguration,
            val connectAll: Boolean) {
        val connections = mutableMapOf<XPeerID, XPeerConnection>()
    }

    private val chains: MutableMap<Long, Chain> = mutableMapOf()
    private val chainIdForBlockchainRid = mutableMapOf<BlockchainRid, Long>()
    protected var isShutDown = false

    var peersConnectionStrategy: PeersConnectionStrategy =
            DefaultPeersConnectionStrategy(this, peerCommConfiguration.myPeerInfo().peerId())

    override fun shutdown() {
        connector.shutdown()
        peersConnectionStrategy.shutdown()
        synchronized(this) {
            isShutDown = true

            chains.forEach { (_, chain) ->
                chain.connections.forEach { (_, conn) -> conn.close() }
            }
            chains.clear()
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
        var ok = true
        if (chainID in chains) {
            disconnectChain(chainID, loggingPrefix)
            ok = false
        }
        chains[chainPeersConfig.chainId] = Chain(chainPeersConfig, autoConnectAll)
        chainIdForBlockchainRid[chainPeersConfig.blockchainRid] = chainPeersConfig.chainId

        if (autoConnectAll) {
            val commConf = chainPeersConfig.commConfiguration
            peersConnectionStrategy.connectAll(chainID, commConf.networkNodes.getPeerIds())
        }

        if (!ok) throw ProgrammerMistake("Error: multiple connections to for one chain")

        logger.debug { "${logger(chainPeersConfig)}: Chain connected: ${chainPeersConfig.chainId}" }
    }

    private fun connectorConnectPeer(chainPeersConfig: XChainPeersConfiguration, peerId: XPeerID) {
        logger.info { "${logger(chainPeersConfig)}: Connecting chain peer: chain = ${chainPeersConfig.chainId}, peer = ${peerName(peerId)}" }

        val peerConnectionDescriptor = XPeerConnectionDescriptor(
                peerId,
                chainPeersConfig.blockchainRid)

        val peerInfo = chainPeersConfig.commConfiguration.resolvePeer(peerId.byteArray)
                ?: throw ProgrammerMistake("Peer ID not found: ${peerId.byteArray.toHex()}")

        val packetEncoder = packetEncoderFactory.create(
                chainPeersConfig.commConfiguration,
                chainPeersConfig.blockchainRid)

        connector.connectPeer(peerConnectionDescriptor, peerInfo, packetEncoder)
    }

    @Synchronized
    override fun connectChainPeer(chainId: Long, peerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        if (peerId !in chain.connections) { // ignore if already connected
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
        }
    }

    @Synchronized
    override fun disconnectChain(chainId: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting chain: $chainId" }

        val chain = chains[chainId]
        if (chain != null) {
            chain.connections.values.forEach { it.close() }
            chain.connections.clear()
            chains.remove(chainId)
            logger.debug { "${loggingPrefix()}: Chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Chain not found: $chainId" }
        }
    }

    @Synchronized
    override fun onPeerConnected(connection: XPeerConnection): XPacketHandler? {
        val descriptor = connection.descriptor()
        logger.info {
            "${logger(descriptor)}: Peer connected: peer = ${peerName(descriptor.peerId)}" +
                    ", blockchainRID: ${descriptor.blockchainRid}" +
                    ", direction: ${descriptor.dir}" +
                    ", (size of c4Brid: ${chainIdForBlockchainRid.size}, size of chains: ${chains.size}) "
        }

        val chainId = chainIdForBlockchainRid[descriptor.blockchainRid]
        if (chainId == null) {
            logger.warn("${logger(descriptor)}: onPeerConnected: Chain ID not found by blockchainRID = ${descriptor.blockchainRid}")
            connection.close()
            return null
        }
        val chain = chains[chainId]
        if (chain == null) {
            logger.warn("${logger(descriptor)}: onPeerConnected: Chain not found by chainID = ${chainId}} / blockchainRID = ${descriptor.blockchainRid}")
            connection.close()
            return null
        }

        return if (!peerCommConfiguration.networkNodes.isNodeBehavingWell(descriptor.peerId, System.currentTimeMillis())) {
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
                    logger.debug { "${logger(descriptor)}: onPeerConnected: Peer connected and replaced previous connection: peer = ${peerName(descriptor.peerId)}" }
                    chain.peerConfig.packetHandler
                } else {
                    connection.close()
                    null
                }
            } else {
                chain.connections[descriptor.peerId] = connection
                logger.debug { "${logger(descriptor)}: onPeerConnected: Peer connected: peer = ${peerName(descriptor.peerId)}" }
                peersConnectionStrategy.connectionEstablished(chainId, connection.descriptor().isOutgoing(), descriptor.peerId)
                chain.peerConfig.packetHandler
            }
        }
    }

    @Synchronized
    override fun onPeerDisconnected(connection: XPeerConnection) {
        val descriptor = connection.descriptor()
        logger.debug {
            "${logger(descriptor)}: Peer disconnected: peer = ${peerName(descriptor.peerId)}" +
                    ", direction: ${descriptor.dir}"
        }

        val chainID = chainIdForBlockchainRid[descriptor.blockchainRid]
        val chain = if (chainID != null) chains[chainID] else null
        if (chain == null) {
            logger.warn("${logger(descriptor)}: Peer disconnected: chain not found by blockchainRID = ${descriptor.blockchainRid} / chainID = $chainID")
            return
        }

        if (chain.connections[descriptor.peerId] == connection) {
            // It's the connection we're using, so we have to remove it
            chain.connections.remove(descriptor.peerId)
        }

        if (chain.connectAll) {
            peersConnectionStrategy.connectionLost(chainID!!, descriptor.peerId, descriptor.isOutgoing())
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
            peerCommConfiguration.myPeerInfo().pubKey.byteArrayKeyOf().toString(),
            blockchainRid
    ).toString()

    private fun logger(descriptor: XPeerConnectionDescriptor): String = loggingPrefix(descriptor.blockchainRid)

    private fun logger(config: XChainPeersConfiguration): String = loggingPrefix(config.blockchainRid)
}