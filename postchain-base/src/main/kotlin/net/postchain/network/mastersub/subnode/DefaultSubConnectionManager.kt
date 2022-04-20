package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.common.ChainsWithOneConnection
import net.postchain.network.common.ConnectionManager
import net.postchain.network.common.LazyPacket
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsConnectedPeersMessage
import net.postchain.network.mastersub.protocol.MsDataMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.subnode.netty.NettySubConnector
import net.postchain.network.peer.XChainPeersConfiguration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule

/**
 * [SubConnectionManager] has only one connection; it's connection to
 * [net.postchain.network.mastersub.master.MasterConnectionManager] of master node.
 */
interface SubConnectionManager : ConnectionManager {

    /**
     * Call this method before [connectChain] to add MsMessage handler for chain [chainId].
     * [SubConnectionManager] will add this handler into the Ms-Message Pipeline of chain [chainId]
     * when [connectChain] will be called.
     */
    fun preAddMsMessageHandler(chainId: Long, handler: MsMessageHandler)

    /**
     * Sends a [MsMessage] to the Master node
     */
    fun sendMessageToMaster(chainId: Long, message: MsMessage)

}


/**
 * While the "master" simply pass on messages, the subnode must deal with the content of the messages,
 * much like a regular peer would.
 */
class DefaultSubConnectionManager(
        private val appConfig: AppConfig,
        private val containerNodeConfig: ContainerNodeConfig
) : SubConnectionManager, SubConnectorEvents {

    companion object : KLogging()

    // We don't bother with factory here
    private val subConnector = NettySubConnector(this)

    // Too much type magic
    private val chains = ChainsWithOneConnection<
            MsMessageHandler,
            SubConnection,
            ChainWithOneMasterConnection>()
    private val connectedPeers = ConcurrentHashMap<BlockchainRid, List<NodeRid>>()

    private val preAddedMsMessageHandlers = mutableMapOf<Long, MsMessageHandler>()
    private val reconnectionTimer = Timer("Reconnection timer")
    private var isShutDown = false

    private val connectedPeersHandler: MsMessageHandler = object : MsMessageHandler {
        override fun onMessage(message: MsMessage) {
            if (message is MsConnectedPeersMessage) {
                connectedPeers[BlockchainRid(message.blockchainRid)] = message.connectedPeers.map(::NodeRid)
            }
        }
    }

    @Synchronized
    override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Connecting master chain: ${chainPeersConfig.log()}" }

        if (isShutDown) {
            logger.warn("${loggingPrefix()}: Already shut down: connecting subnode chains is not possible")
        } else {
            if (chains.hasChain(chainPeersConfig.chainId)) {
                disconnectChain(loggingPrefix, chainPeersConfig.chainId)
            }

            val chain = ChainWithOneMasterConnection(chainPeersConfig)
            chain.addMsMessageHandler(preAddedMsMessageHandlers.remove(chainPeersConfig.chainId))
            chain.addMsMessageHandler(connectedPeersHandler)
            chains.add(chain)
            connectToMaster(chain)

            logger.debug { "${logger(chain)}: Master chain connected: ${chainPeersConfig.log()}" }
        }
    }


    private fun connectToMaster(chain: ChainWithOneMasterConnection) {
        logger.info { "${logger(chain)}: Connecting to master node: ${chain.log()}" }

        val masterNode = PeerInfo(
                containerNodeConfig.masterHost,
                containerNodeConfig.masterPort,
                byteArrayOf() // It's not necessary here
        )

        val connectionDescriptor = SubConnectionDescriptor(chain.config.blockchainRid, chain.peers)
        subConnector.connectMaster(masterNode, connectionDescriptor)
        logger.info { "${logger(chain)}: Connected to master node: ${chain.log()}" }
    }

    /**
     * Sending a packet means we know to what peer in the "real" network should have the
     * message, but we are of course not allowed to send it directly.
     * Instead we send a [MsMessage] to the master.
     */
    @Synchronized
    override fun sendPacket(data: LazyPacket, chainId: Long, nodeRid: NodeRid) {
        val chain = chains.getOrThrow(chainId)
        if (chain.isConnected()) {
            val message = MsDataMessage(
                    chain.config.blockchainRid.data,
                    appConfig.pubKeyByteArray,
                    nodeRid.byteArray,
                    data()
            )
            chain.getConnection()!!.sendPacket { MsCodec.encode(message) }
        } else {
            logger.error("${logger(chain)}: sendPacket() - Master is disconnected so cannot send packet to ${chain.log()}")
        }
    }

    @Synchronized
    override fun broadcastPacket(data: LazyPacket, chainId: Long) {
        val chain = chains.getOrThrow(chainId)
        chain.peers.forEach { peerId ->
            sendPacket(data, chainId, NodeRid(peerId))
        }
    }

    override fun getConnectedNodes(chainId: Long): List<NodeRid> {
        return chains.get(chainId)
                ?.let { connectedPeers[it.getBlockchainRid()] }
                ?: emptyList()
    }

    @Synchronized
    override fun disconnectChain(loggingPrefix: () -> String, chainId: Long) {
        logger.debug { "${loggingPrefix()}: Disconnecting master chain: $chainId" }

        val chain = chains.remove(chainId)
        if (chain != null) {
            chain.removeAndCloseConnection()
            logger.debug { "${loggingPrefix()}: Master chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Master chain is not connected: $chainId" }
        }
    }

    @Synchronized
    override fun shutdown() {
        if (!isShutDown) {
            isShutDown = true

            reconnectionTimer.cancel()
            reconnectionTimer.purge()

            chains.removeAllAndClose()

            subConnector.shutdown()
        }
    }

    // ----------------------------------
    // After/Before connections hooks
    // ----------------------------------

    @Synchronized
    override fun onMasterConnected(
            descriptor: SubConnectionDescriptor,
            connection: SubConnection
    ): MsMessageHandler? {

        logger.info { "${logger(descriptor)}: Master node connected: blockchainRid: ${descriptor.blockchainRid.toShortHex()}" }

        val chain = chains.get(descriptor.blockchainRid)
        return when {
            chain == null -> {
                logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid = ${descriptor.blockchainRid.toShortHex()}")
                connection.close()
                null
            }
            chain.isConnected() -> {
                logger.debug { "${logger(descriptor)}: Master node already connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                // Don't close connection here, just return handler
                chain.getPacketHandler()
            }
            else -> {
                logger.debug { "${logger(descriptor)}: Master node connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                chain.setConnection(connection)
                chain.getPacketHandler()
            }
        }
    }

    @Synchronized
    override fun onMasterDisconnected(
            descriptor: SubConnectionDescriptor,
            connection: SubConnection
    ) {
        logger.info { "${logger(descriptor)}: Master node disconnected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }

        val chain = chains.get(descriptor.blockchainRid)
        if (chain == null) {
            logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid: ${descriptor.blockchainRid.toShortHex()}")
            connection.close()
        } else {
            if (chain.getConnection() !== connection) {
                connection.close()
            }
            chain.removeAndCloseConnection()

            // Reconnecting to the Master
            reconnect(chain)
        }
    }

    // ----------------------------------
    //  [SubConnectionManager] impl
    // ----------------------------------

    override fun preAddMsMessageHandler(chainId: Long, handler: MsMessageHandler) {
        preAddedMsMessageHandlers[chainId] = handler
    }

    override fun sendMessageToMaster(chainId: Long, message: MsMessage) {
        val chain = chains.getOrThrow(chainId)
        if (chain.isConnected()) {
            chain.getConnection()!!.sendPacket { MsCodec.encode(message) }
        } else {
            logger.error("${logger(chain)}: Can't send packet b/c no connection to master node for ${chain.log()}")
        }
    }

    // ----------------------------------
    // [NetworkTopology] impl
    // ----------------------------------

    // Nothing
    override fun getNodesTopology(): Map<String, Map<String, String>> = emptyMap()

    // Nothing
    override fun getNodesTopology(chainIid: Long): Map<NodeRid, String> = emptyMap()

    // ----------------------------------
    // Private
    // ----------------------------------
    private fun reconnect(chain: ChainWithOneMasterConnection) {
        val timeUnit = "ms"
        val timeDelay = 15_000L // TODO: [POS-129]: Implement exponential delay connection strategy
        val brid = chain.config.blockchainRid.toShortHex()
        logger.info("${logger(chain)}: Reconnecting in $timeDelay $timeUnit to master node: blockchainRid: $brid")
        reconnectionTimer.schedule(timeDelay) {
            logger.info("${logger(chain)}: Reconnecting to master node: blockchainRid: $brid")
            connectToMaster(chain)
        }
    }

    private fun loggerPrefix(blockchainRid: BlockchainRid): String =
            BlockchainProcessName(appConfig.pubKey.asString, blockchainRid).toString()

    private fun logger(descriptor: SubConnectionDescriptor): String = loggerPrefix(descriptor.blockchainRid)

    private fun logger(chain: ChainWithOneMasterConnection): String = loggerPrefix(chain.config.blockchainRid)
}
