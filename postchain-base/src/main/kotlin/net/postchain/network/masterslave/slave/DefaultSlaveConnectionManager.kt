package net.postchain.network.masterslave.slave

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.config.node.NodeConfig
import net.postchain.core.ProgrammerMistake
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.masterslave.MsConnection
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.masterslave.protocol.MsDataMessage
import net.postchain.network.masterslave.protocol.MsGetBlockchainConfigMessage
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.masterslave.slave.netty.NettySlaveConnector
import net.postchain.network.x.LazyPacket
import net.postchain.network.x.XChainPeersConfiguration
import net.postchain.network.x.XConnectionManager
import net.postchain.network.x.XPeerID
import java.util.*
import kotlin.concurrent.schedule

/**
 * [SlaveConnectionManager] has only one connection; it's connection to
 * [net.postchain.network.masterslave.master.MasterConnectionManager] of master node.
 */
interface SlaveConnectionManager : XConnectionManager {
    fun setMsMessageHandler(chainId: Long, handler: MsMessageHandler)
    fun requestBlockchainConfig(chainId: Long)
}


class DefaultSlaveConnectionManager(
        private val nodeConfig: NodeConfig
) : SlaveConnectionManager, SlaveConnectorEvents {

    companion object : KLogging()

    private val slaveConnector = NettySlaveConnector(this)
    private val chains = mutableMapOf<Long, Chain>()
    private val msMessageHandlers = mutableMapOf<Long, MsMessageHandler>()
    private val reconnectionTimer = Timer("Reconnection timer")
    private var isShutDown = false

    private class Chain(val config: XChainPeersConfiguration, msMessageHandlerSupplier: (Long) -> MsMessageHandler?) {
        val peers: List<ByteArray> = (config.commConfiguration as SlavePeerCommConfig).peers
        val msMessageHandler: MsMessageHandler = object : MsMessageHandler {
            override fun onMessage(message: MsMessage) {
                when (message) {
                    is MsDataMessage -> config.packetHandler(message.payload, XPeerID(message.source))
                    else -> msMessageHandlerSupplier(config.chainId)?.onMessage(message)
                }
            }
        }
        var connection: MsConnection? = null
    }

    @Synchronized
    override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        val logMsg = "chainId: ${chainPeersConfig.chainId}, blockchainRid: ${chainPeersConfig.blockchainRid.toShortHex()}"
        logger.debug { "${loggingPrefix()}: Connecting master chain: $logMsg" }

        if (isShutDown) {
            logger.warn("${loggingPrefix()}: Already shut down: connecting slave chains is not possible")
        } else {
            if (chainPeersConfig.chainId in chains) {
                disconnectChain(chainPeersConfig.chainId, loggingPrefix)
            }

            val chain = Chain(chainPeersConfig) { chainId -> msMessageHandlers[chainId] }
            chains[chainPeersConfig.chainId] = chain
            connectToMaster(chain)

            logger.debug { "${logger(chain)}: Master chain connected: $logMsg" }
        }
    }

    override fun connectChainPeer(chainId: Long, peerId: XPeerID) {
        logger.debug { "connectChainPeer: Not implemented" }
    }

    override fun isPeerConnected(chainId: Long, peerId: XPeerID): Boolean {
        logger.debug { "isPeerConnected: Not implemented" }
        return false
    }

    override fun getConnectedPeers(chainId: Long): List<XPeerID> {
        logger.debug { "getConnectedPeers: Not implemented" }
        return emptyList()
    }

    private fun connectToMaster(chain: Chain) {
        val logMsg = "chainId: ${chain.config.chainId}, blockchainRid: ${chain.config.blockchainRid.toShortHex()}"
        logger.info { "${logger(chain)}: Connecting to master node: $logMsg" }

        val masterNode = PeerInfo(
                nodeConfig.masterHost,
                nodeConfig.masterPort,
                byteArrayOf() // It's not necessary here
        )

        val connectionDescriptor = SlaveConnectionDescriptor(chain.config.blockchainRid, chain.peers)
        slaveConnector.connectMaster(masterNode, connectionDescriptor)
        logger.info { "${logger(chain)}: Connected to master node: $logMsg" }
    }

    @Synchronized
    override fun sendPacket(data: LazyPacket, chainId: Long, peerId: XPeerID) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Master chain not found: $chainId")
        if (chain.connection != null) {
            val message = MsDataMessage(
                    nodeConfig.pubKeyByteArray,
                    peerId.byteArray,
                    chain.config.blockchainRid.data,
                    data())
            chain.connection?.sendPacket { MsCodec.encode(message) }
        } else {
            logger.error("${logger(chain)}: Can't send packet to master node blockchainRid = ${chain.config.blockchainRid}")
        }
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainId: Long) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Master chain not found: $chainId")
        chain.peers.forEach { peerId -> sendPacket(data, chainId, XPeerID(peerId)) }
    }

    override fun disconnectChainPeer(chainId: Long, peerId: XPeerID) {
        logger.debug { "disconnectChainPeer: Not implemented" }
    }

    @Synchronized
    override fun disconnectChain(chainId: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting master chain: $chainId" }

        val chain = chains.remove(chainId)
        if (chain != null) {
            chain.connection?.close()
            chain.connection = null
            logger.debug { "${loggingPrefix()}: Master chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Master chain is not connected: $chainId" }
        }
    }

    override fun getPeersTopology(): Map<String, Map<String, String>> {
        logger.debug { "getPeersTopology: Not implemented" }
        return emptyMap()
    }

    override fun getPeersTopology(chainID: Long): Map<XPeerID, String> {
        logger.debug { "getPeersTopology(chainId): Not implemented" }
        return emptyMap()
    }

    @Synchronized
    override fun shutdown() {
        isShutDown = true
        reconnectionTimer.cancel()
        reconnectionTimer.purge()

        chains.values.forEach { it.connection?.close() }
        chains.clear()

        slaveConnector.shutdown()
    }

    @Synchronized
    override fun onMasterConnected(descriptor: SlaveConnectionDescriptor, connection: MsConnection): MsMessageHandler? {
        logger.info { "${logger(descriptor)}: Master node connected: blockchainRid: ${descriptor.blockchainRid.toShortHex()}" }

        val chain = findChainByBrid(descriptor.blockchainRid)
        return when {
            chain == null -> {
                logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid = ${descriptor.blockchainRid.toShortHex()}")
                connection.close()
                null
            }
            chain.connection != null -> {
                logger.debug { "${logger(descriptor)}: Master node already connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                // Don't close connection here, just return handler
                chain.msMessageHandler
            }
            else -> {
                logger.debug { "${logger(descriptor)}: Master node connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                chain.connection = connection
                chain.msMessageHandler
            }
        }
    }

    @Synchronized
    override fun onMasterDisconnected(descriptor: SlaveConnectionDescriptor, connection: MsConnection) {
        logger.debug { "${logger(descriptor)}: Master node disconnected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }

        val chain = findChainByBrid(descriptor.blockchainRid)
        if (chain == null) {
            logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid: ${descriptor.blockchainRid.toShortHex()}")
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

    override fun setMsMessageHandler(chainId: Long, handler: MsMessageHandler) {
        msMessageHandlers[chainId] = handler
    }

    override fun requestBlockchainConfig(chainId: Long) {
        val chain = chains[chainId] ?: throw ProgrammerMistake("Master chain not found: $chainId")
        if (chain.connection != null) {
            val message = MsGetBlockchainConfigMessage(chain.config.blockchainRid.data)
            chain.connection?.sendPacket { MsCodec.encode(message) }
        } else {
            logger.error("${logger(chain)}: Can't send packet to master node blockchainRid = ${chain.config.blockchainRid.toShortHex()}")
        }
    }

    private fun reconnect(chain: Chain) {
        val timeUnit = "ms"
        val timeDelay = 15_000L // TODO: [POS-129]: Implement exponential delay connection strategy
        val brid = chain.config.blockchainRid.toShortHex()
        logger.info { "${logger(chain)}: Reconnecting in $timeDelay $timeUnit to master node: blockchainRid: $brid" }
        reconnectionTimer.schedule(timeDelay) {
            logger.info { "${logger(chain)}: Reconnecting to master node: blockchainRid: $brid" }
            connectToMaster(chain)
        }
    }

    private fun findChainByBrid(blockchainRid: BlockchainRid): Chain? {
        return chains.values.firstOrNull { it.config.blockchainRid == blockchainRid }
    }

    private fun loggerPrefix(blockchainRid: BlockchainRid): String =
            BlockchainProcessName(nodeConfig.pubKey, blockchainRid).toString()

    private fun logger(descriptor: SlaveConnectionDescriptor): String = loggerPrefix(descriptor.blockchainRid)

    private fun logger(chain: Chain): String = loggerPrefix(chain.config.blockchainRid)

}
