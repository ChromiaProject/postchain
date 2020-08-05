package net.postchain.network.masterslave.slave

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.config.node.NodeConfig
import net.postchain.core.ProgrammerMistake
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.masterslave.NodeConnection
import net.postchain.network.masterslave.PacketHandler
import net.postchain.network.masterslave.protocol.DataMsMessage
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.masterslave.slave.netty.NettySlaveConnector
import net.postchain.network.x.LazyPacket
import net.postchain.network.x.XChainPeersConfiguration
import net.postchain.network.x.XPacketHandler
import net.postchain.network.x.XPeerID
import nl.komponents.kovenant.task
import java.util.*
import kotlin.concurrent.schedule

class DefaultSlaveConnectionManager(
        private val nodeConfig: NodeConfig
) : SlaveConnectionManager, SlaveConnectorEvents {

    companion object : KLogging()

    // TODO: [POS-129]: Extract to factory
    private val slaveConnector = NettySlaveConnector(this)

    protected class MasterChain(config: XChainPeersConfiguration) {
        val peers = config.commConfiguration.networkNodes
        val chainId = config.chainId
        val blockchainRid = config.blockchainRid
        val xPacketHandler: XPacketHandler = config.packetHandler
        val packetHandler: PacketHandler = { msg ->
            xPacketHandler(msg.messageData, XPeerID(msg.source))
        }
        val singers: List<ByteArray> = (config.commConfiguration as SlavePeerCommConfiguration).singers
        var connection: NodeConnection? = null
    }

    private val chains = mutableMapOf<Long, MasterChain>()
    private val chainsByBrid = mutableMapOf<BlockchainRid, MasterChain>()
    private var isShutDown = false

    @Synchronized
    override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        val chainStr = "chainId: ${chainPeersConfig.chainId}, blockchainRid: ${chainPeersConfig.blockchainRid.toShortHex()}"
        logger.debug { "${loggingPrefix()}: Connecting master chain: $chainStr" }

        if (isShutDown) throw ProgrammerMistake("Already shut down: connecting slave chains is not possible")
        if (chainPeersConfig.chainId in chains) {
            disconnectChain(chainPeersConfig.chainId, loggingPrefix)
        }

        val chain = MasterChain(chainPeersConfig)
        chains[chainPeersConfig.chainId] = chain
        chainsByBrid[chainPeersConfig.blockchainRid] = chain
        connectMaster(chain)

        logger.debug { "${logger(chain)}: Master chain connected: $chainStr" }
    }

    private fun connectMaster(chain: MasterChain) {
        val chainStr = "chainId: ${chain.chainId}, blockchainRid: ${chain.blockchainRid.toShortHex()}"
        logger.info { "${logger(chain)}: Connecting to master node: $chainStr" }

        val masterNode = PeerInfo(
                nodeConfig.masterHost,
                nodeConfig.masterPort,
                byteArrayOf() // Is not necessary here
        )

        val connectionDescriptor = SlaveConnectionDescriptor(chain.blockchainRid, chain.singers)

        task {
            slaveConnector.connectMaster(masterNode, connectionDescriptor)
            logger.info { "${logger(chain)}: Connected to master node: $chainStr" }
        }
    }

    override fun connectChainPeer(chainId: Long, targetPeerId: XPeerID) {
        TODO("POS-129: Won't be implemented")
    }

    override fun isPeerConnected(chainId: Long, targetPeerId: XPeerID): Boolean {
        TODO("POS-129: Won't be implemented")
    }

    override fun getConnectedPeers(chainId: Long): List<XPeerID> {
        TODO("POS-129: Won't be implemented")
    }

    @Synchronized
    override fun sendPacket(data: LazyPacket, chainId: Long, targetPeerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Master chain not found: $chainId")
        if (chain.connection != null) {
            val message = DataMsMessage(
                    nodeConfig.pubKeyByteArray,
                    targetPeerId.byteArray,
                    chain.blockchainRid.data,
                    data())

            val bytes = MsCodec.encode(message)
            chain.connection?.sendPacket { bytes }
        } else {
            logger.error("${logger(chain)}: Can't send packet to master node blockchainRid = ${chain.blockchainRid}")
        }
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainId: Long) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Master chain not found: $chainId")
        chain.peers.filterAndRunActionOnPeers({ peers, _ -> peers.values.toSet() }) { peerInfo ->
            sendPacket(data, chainId, XPeerID(peerInfo.pubKey))
        }
    }

    override fun disconnectChainPeer(chainId: Long, targetPeerId: XPeerID) {
        TODO("POS-129: Won't be implemented")
    }

    @Synchronized
    override fun disconnectChain(chainId: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting master chain: $chainId" }

        val chain = chains[chainId]
        if (chain != null) {
            chain.connection?.close()
            chain.connection = null
            chains.remove(chainId)
            chainsByBrid.entries.removeIf { it.value.chainId == chainId }
            logger.debug { "${loggingPrefix()}: Master chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Master chain is not connected: $chainId" }
        }
    }

    override fun getPeersTopology(): Map<String, Map<String, String>> {
        return emptyMap() // TODO: [POS-129]: Implement it for diagnostic
    }

    override fun getPeersTopology(chainId: Long): Map<XPeerID, String> {
        return emptyMap() // TODO: [POS-129]: Implement it for diagnostic
    }

    @Synchronized
    override fun shutdown() {
        isShutDown = true

        chains.forEach { (_, chain) -> chain.connection?.close() }
        chains.clear()
        chainsByBrid.clear()

        slaveConnector.shutdown()
    }

    @Synchronized
    override fun onMasterConnected(descriptor: SlaveConnectionDescriptor, connection: NodeConnection): PacketHandler? {
        logger.info { "${logger(descriptor)}: Master node connected: blockchainRid: ${descriptor.blockchainRid}" }

        val chain = chainsByBrid[descriptor.blockchainRid]
        return when {
            chain == null -> {
                logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid = ${descriptor.blockchainRid}")
                connection.close()
                null
            }
            chain.connection != null -> {
                logger.debug { "${logger(descriptor)}: Master node already connected: blockchainRid = ${descriptor.blockchainRid}" }
                // Don't close connection here, just return handler
                chain.packetHandler
            }
            else -> {
                logger.debug { "${logger(descriptor)}: Master node connected: blockchainRid = ${descriptor.blockchainRid}" }
                chain.connection = connection
                chain.packetHandler
            }
        }
    }

    @Synchronized
    override fun onMasterDisconnected(descriptor: SlaveConnectionDescriptor, connection: NodeConnection) {
        logger.debug { "${logger(descriptor)}: Master node disconnected: blockchainRid = ${descriptor.blockchainRid}" }

        val chain = chainsByBrid[descriptor.blockchainRid]
        if (chain == null) {
            logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid: ${descriptor.blockchainRid}")
            connection.close()
        } else {
            if (chain.connection !== connection) {
                connection.close()
            }
            chain.connection?.close()
            chain.connection = null

            // Reconnecting to the Master
            reconnect(chain)
        }
    }

    private fun reconnect(chain: MasterChain) {
        val timeUnit = "seconds"
        val timeDelay = 15L
        logger.info { "${logger(chain)}: Reconnecting in $timeDelay $timeUnit to master node: blockchainRid: ${chain.blockchainRid}" }
        Timer("Reconnecting to master").schedule(timeDelay) {
            logger.info { "${logger(chain)}: Reconnecting to master node: blockchainRid: ${chain.blockchainRid}" }
            connectMaster(chain)
        }
    }

    private fun loggingPrefix(blockchainRid: BlockchainRid): String = BlockchainProcessName(
            nodeConfig.pubKey, blockchainRid
    ).toString()

    private fun logger(descriptor: SlaveConnectionDescriptor): String = loggingPrefix(descriptor.blockchainRid)

    private fun logger(chain: MasterChain): String = loggingPrefix(chain.blockchainRid)

}