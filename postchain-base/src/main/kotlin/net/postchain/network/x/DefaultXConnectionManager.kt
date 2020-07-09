// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.CryptoSystem
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.ExponentialDelay
import net.postchain.common.toHex
import net.postchain.core.ProgrammerMistake
import net.postchain.core.byteArrayKeyOf
import net.postchain.debug.BlockchainProcessName
import net.postchain.devtools.PeerNameHelper.peerName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import net.postchain.network.netty2.NettyClientPeerConnection
import nl.komponents.kovenant.task
import java.util.*
import kotlin.concurrent.schedule

open class DefaultXConnectionManager<PacketType>(
        connectorFactory: XConnectorFactory<PacketType>,
        val peerCommConfiguration: PeerCommConfiguration,
        private val packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        private val packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        cryptoSystem: CryptoSystem,
        private val peersConnectionStrategy: PeersConnectionStrategy = DefaultPeersConnectionStrategy
) : XConnectionManager, XConnectorEvents {

    companion object : KLogging()

    private val connector = connectorFactory.createConnector(
            peerCommConfiguration.myPeerInfo(),
            packetDecoderFactory.create(peerCommConfiguration),
            this) // TODO: [POS-129]: ?

    protected class Chain(
            val peerConfig: XChainPeersConfiguration,
            val connectAll: Boolean) {
        val neededConnections = mutableSetOf<XPeerID>()
        val connections = mutableMapOf<XPeerID, XPeerConnection>()
    }

    private val chains: MutableMap<Long, Chain> = mutableMapOf()
    private val chainIDforBlockchainRID = mutableMapOf<BlockchainRid, Long>()
    protected var isShutDown = false

    private val peerToDelayMap: MutableMap<XPeerID, ExponentialDelay> = mutableMapOf()


    @Synchronized
    override fun shutdown() {
        isShutDown = true

        chains.forEach { (_, chain) ->
            chain.connections.forEach { (_, conn) -> conn.close() }
        }
        chains.clear()

        connector.shutdown()
    }

    @Synchronized
    override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        logger.debug {
            "${loggingPrefix()}: Connecting chain: ${chainPeersConfig.chainId}" +
                    ", blockchainRID: ${chainPeersConfig.blockchainRid.toShortHex()}"
        }

        if (isShutDown) throw ProgrammerMistake("Already shut down")
        var ok = true
        if (chainPeersConfig.chainId in chains) {
            disconnectChain(chainPeersConfig.chainId, loggingPrefix)
            ok = false
        }
        chains[chainPeersConfig.chainId] = Chain(chainPeersConfig, autoConnectAll)
        chainIDforBlockchainRID[chainPeersConfig.blockchainRid] = chainPeersConfig.chainId

        if (autoConnectAll) {
            peersConnectionStrategy.forEach(chainPeersConfig.commConfiguration) {
                connectPeer(chainPeersConfig, it.pubKey.byteArrayKeyOf())
            }
        }

        if (!ok) throw ProgrammerMistake("Error: multiple connections to for one chain")

        logger.debug { "${logger(chainPeersConfig)}: Chain connected: ${chainPeersConfig.chainId}" }
    }

    private fun connectPeer(chainPeersConfig: XChainPeersConfiguration, targetPeerId: XPeerID) {
        logger.info { "${logger(chainPeersConfig)}: Connecting chain peer: chain = ${chainPeersConfig.chainId}, peer = ${peerName(targetPeerId)}" }

        val peerConnectionDescriptor = XPeerConnectionDescriptor(
                targetPeerId,
                chainPeersConfig.blockchainRid)

        val targetPeerInfo = chainPeersConfig.commConfiguration.resolvePeer(targetPeerId.byteArray)
                ?: throw ProgrammerMistake("Peer ID not found: ${targetPeerId.byteArray.toHex()}")

        val packetEncoder = packetEncoderFactory.create(
                chainPeersConfig.commConfiguration,
                chainPeersConfig.blockchainRid)

        task {
            connector.connectPeer(peerConnectionDescriptor, targetPeerInfo, packetEncoder)
        }
    }

    @Synchronized
    override fun connectChainPeer(chainId: Long, targetPeerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        if (targetPeerId !in chain.connections) { // ignore if already connected
            connectPeer(chain.peerConfig, targetPeerId)
        }
    }

    @Synchronized
    override fun isPeerConnected(chainId: Long, targetPeerId: XPeerID): Boolean {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        return targetPeerId in chain.connections
    }

    @Synchronized
    override fun getConnectedPeers(chainId: Long): List<XPeerID> {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        return chain.connections.keys.toList()
    }

    @Synchronized
    override fun sendPacket(data: LazyPacket, chainId: Long, targetPeerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        chain.connections[targetPeerId]?.sendPacket(data)
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
    override fun disconnectChainPeer(chainId: Long, targetPeerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Chain ID not found: $chainId")
        val conn = chain.connections[targetPeerId]
        if (conn != null) {
            conn.close()
            chain.connections.remove(targetPeerId)
        }
    }

    @Synchronized
    override fun disconnectChain(chainId: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting chain: $chainId" }

        val chain = chains[chainId]
        if (chain != null) {
            chain.connections.values.forEach(XPeerConnection::close)
            chain.connections.clear()
            chains.remove(chainId)
            logger.debug { "${loggingPrefix()}: Chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Chain not found: $chainId" }
        }
    }

    @Synchronized
    override fun onPeerConnected(descriptor: XPeerConnectionDescriptor, connection: XPeerConnection): XPacketHandler? {
        logger.info {
            "${logger(descriptor)}: Peer connected: peer = ${peerName(descriptor.peerId)}" +
                    ", blockchainRID: ${descriptor.blockchainRid}" +
                    ", (size of c4Brid: ${chainIDforBlockchainRID.size}, size of chains: ${chains.size}) "
        }

        val chainId = chainIDforBlockchainRID[descriptor.blockchainRid]
        val chain = if (chainId != null) chains[chainId] else null
        if (chain == null) {
            logger.warn("${logger(descriptor)}: onPeerConnected: Chain not found by blockchainRID = ${descriptor.blockchainRid} / chainID = $chainId")
            connection.close()
            return null
        }

        return if (!peerCommConfiguration.networkNodes.isNodeBehavingWell(descriptor.peerId, System.currentTimeMillis())) {
            logger.debug { "${logger(descriptor)}: onPeerConnected: Peer not behaving well, so ignore: peer = ${peerName(descriptor.peerId)}" }
            // TODO: [POS-129]: Should we `connection.close()` here? See `if (chain == null)` above.
            null
        } else if (chain.connections[descriptor.peerId] != null) {
            logger.debug { "${logger(descriptor)}: onPeerConnected: Peer already connected: peer = ${peerName(descriptor.peerId)}" }
            // TODO: [POS-129]: Should we `connection.close()` here? See `if (chain == null)` above.
            null
        } else {
            chain.connections[descriptor.peerId] = connection
            logger.debug { "${logger(descriptor)}: onPeerConnected: Peer connected: peer = ${peerName(descriptor.peerId)}" }
            peerToDelayMap.remove(descriptor.peerId) // We are connected, with means we must clear the re-connect delay
            chain.peerConfig.packetHandler
        }
    }

    @Synchronized
    override fun onPeerDisconnected(descriptor: XPeerConnectionDescriptor, connection: XPeerConnection) {
        logger.debug { "${logger(descriptor)}: Peer disconnected: peer = ${peerName(descriptor.peerId)}" }

        // Closing local connection entity
        // TODO: [POS-129]: Close single time only of connection == oldConnection (see below)
        connection.close()

        val chainID = chainIDforBlockchainRID[descriptor.blockchainRid]
        val chain = if (chainID != null) chains[chainID] else null
        if (chain == null) {
            logger.warn("${logger(descriptor)}: Peer disconnected: chain not found by blockchainRID = ${descriptor.blockchainRid} / chainID = $chainID")
            return
        }

        val oldConnection = chain.connections[descriptor.peerId]
        if (oldConnection != null) {
            oldConnection.close()
            chain.connections.remove(descriptor.peerId)
        }

        // TODO: [POS-129]: See connection strategy !!!
        // Reconnecting if connectionType is CLIENT
        if (connection is NettyClientPeerConnection<*>) {
            if (chain.connectAll || (descriptor.peerId in chain.neededConnections)) {
                reconnect(chain.peerConfig, descriptor.peerId)
            }
        }
    }

    override fun getPeersTopology(): Map<String, Map<String, String>> {
        return chains
                .mapKeys { (id, chain) -> id to chain.peerConfig.blockchainRid.toHex() }
                .mapValues { (idToRid, _) -> getPeersTopology(idToRid.first).mapKeys { (k, _) -> k.toString() } }
                .mapKeys { (idToRid, _) -> idToRid.second }
    }

    override fun getPeersTopology(chainId: Long): Map<XPeerID, String> {
        return chains[chainId]
                ?.connections
                ?.mapValues { connection ->
                    // TODO: Fix this
                    when (connection.value) {
                        is NettyClientPeerConnection<*> -> "c-s"
                        else -> "s-c"
                    }.plus(", " + connection.value.remoteAddress())
                }
                ?: emptyMap()
    }

    private fun reconnect(peerConfig: XChainPeersConfiguration, peerId: XPeerID) {
        val delay = peerToDelayMap.computeIfAbsent(peerId) { ExponentialDelay() }
        val (timeUnit, timeDelay) = prettyDelay(delay)
        logger.info { "${logger(peerConfig)}: Reconnecting in $timeDelay $timeUnit to peer = ${peerName(peerId)}" }
        Timer("Reconnecting").schedule(delay.getDelayMillis()) {
            logger.info { "${logger(peerConfig)}: Reconnecting to peer: peer = ${peerName(peerId)}" }
            connectPeer(peerConfig, peerId)
        }
    }

    private fun loggingPrefix(blockchainRid: BlockchainRid): String = BlockchainProcessName(
            peerCommConfiguration.myPeerInfo().pubKey.byteArrayKeyOf().toString(),
            blockchainRid
    ).toString()

    private fun logger(descriptor: XPeerConnectionDescriptor): String = loggingPrefix(descriptor.blockchainRid)

    private fun logger(config: XChainPeersConfiguration): String = loggingPrefix(config.blockchainRid)

    private fun prettyDelay(delay: ExponentialDelay): Pair<String, Long> {
        return if (delay.getDelayMillis() < 1000) {
            "milliseconds" to delay.getDelayMillis()
        } else {
            "seconds" to delay.getDelayMillis() / 1000
        }
    }
}