package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.NodeRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.common.ChainsWithOneConnection
import net.postchain.network.common.ConnectionManager
import net.postchain.network.common.LazyPacket
import net.postchain.network.mastersub.MasterSubQueryManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsConnectedPeersMessage
import net.postchain.network.mastersub.protocol.MsDataMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.subnode.netty.NettySubConnector
import net.postchain.network.peer.XChainPeersConfiguration
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * [SubConnectionManager] has only one connection; it's connection to
 * [net.postchain.network.mastersub.master.MasterConnectionManager] of master node.
 */
interface SubConnectionManager : ConnectionManager {
    val masterSubQueryManager: MasterSubQueryManager

    /**
     * Call this method before [connectChain] to add MsMessage handler for chain [chainId].
     * [SubConnectionManager] will add this handler into the Ms-Message Pipeline of chain [chainId]
     * when [connectChain] will be called.
     */
    fun preAddMsMessageHandler(chainId: Long, handler: MsMessageHandler)

    /**
     * Sends a [MsMessage] to the Master node
     */
    fun sendMessageToMaster(chainId: Long, message: MsMessage): Boolean

}


/**
 * While the "master" simply pass on messages, the subnode must deal with the content of the messages,
 * much like a regular peer would.
 */
class DefaultSubConnectionManager(
        private val appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
) : SubConnectionManager, SubConnectorEvents {

    companion object : KLogging()

    override val masterSubQueryManager = MasterSubQueryManager { _, message ->
        val connection = queryConnection
        if (connection != null) {
            connection.sendPacket { MsCodec.encode(message) }
            true
        } else false
    }

    // We don't bother with factory here
    private val subConnector = NettySubConnector(this)

    // Too much type magic
    private val chains = ChainsWithOneConnection<
            MsMessageHandler,
            SubConnection,
            ChainWithOneMasterConnection>()
    private val connectedPeers = ConcurrentHashMap<BlockchainRid, List<NodeRid>>()

    private val preAddedMsMessageHandlers = mutableMapOf<Long, MutableList<MsMessageHandler>>()
    private val reconnectionExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val reconnectionScheduledForChain = mutableMapOf<BlockchainRid, ScheduledFuture<*>>()
    private var reconnectionScheduledForQuery: ScheduledFuture<*>? = null
    private val reconnectDelay = Duration.ofSeconds(15)
    private var isShutDown = false

    private val connectedPeersHandler: MsMessageHandler = object : MsMessageHandler {
        override fun onMessage(message: MsMessage) {
            if (message is MsConnectedPeersMessage) {
                connectedPeers[BlockchainRid(message.blockchainRid)] = message.connectedPeers.map(::NodeRid)
            }
        }
    }

    private val masterNodePeerInfo = PeerInfo(
            containerNodeConfig.masterHost,
            containerNodeConfig.masterPort,
            byteArrayOf() // It's not necessary here
    )

    private var queryConnection: SubConnection? = null
    private val queryConnectionDescriptor = SubConnectionDescriptor(null, listOf(), containerNodeConfig.containerIID)

    init {
        logger.debug("Establishing query connection to master node")
        subConnector.connectMaster(masterNodePeerInfo, queryConnectionDescriptor)
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
            preAddedMsMessageHandlers.remove(chainPeersConfig.chainId)?.forEach { chain.addMsMessageHandler(it) }
            chain.addMsMessageHandler(connectedPeersHandler)
            chain.addMsMessageHandler(masterSubQueryManager)
            chains.add(chain)
            connectToMaster(chain)

            logger.debug { "${logger(chain)}: Master chain connected: ${chainPeersConfig.log()}" }
        }
    }


    private fun connectToMaster(chain: ChainWithOneMasterConnection) {
        val connectionDescriptor = SubConnectionDescriptor(chain.config.blockchainRid, chain.peers, containerNodeConfig.containerIID)

        logger.info { "${logger(chain)}: Connecting to master node ${masterNodePeerInfo.host}:${masterNodePeerInfo.port}, chanId: ${chain.log()}" }
        subConnector.connectMaster(masterNodePeerInfo, connectionDescriptor)
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
                    appConfig.pubKeyByteArray,
                    nodeRid.data,
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

            reconnectionExecutor.shutdownNow()
            reconnectionExecutor.awaitTermination(2000, TimeUnit.MILLISECONDS)

            chains.removeAllAndClose()
            queryConnection?.close()

            subConnector.shutdown()
        }
    }

    // ----------------------------------
    // After/Before connections hooks
    // ----------------------------------

    @Synchronized
    override fun onMasterConnected(
            descriptor: SubConnectionDescriptor,
            connection: SubConnection,
    ): MsMessageHandler? {
        return if (descriptor.blockchainRid == null) {
            queryConnection?.close()
            queryConnection = connection

            logger.debug("Query connection to master established")
            masterSubQueryManager
        } else {
            val chain = chains.get(descriptor.blockchainRid)
            when {
                chain == null -> {
                    logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid = ${descriptor.blockchainRid.toShortHex()}")
                    connection.close()
                    null
                }

                chain.isConnected() -> {
                    logger.warn { "${logger(descriptor)}: Master node already connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                    // Don't close connection here, just return handler
                    chain.getPacketHandler()
                }

                else -> {
                    logger.info { "${logger(descriptor)}: Master node connected: blockchainRid: ${descriptor.blockchainRid.toShortHex()}" }
                    chain.setConnection(connection)
                    chain.getPacketHandler()
                }
            }
        }
    }

    @Synchronized
    override fun onMasterDisconnected(
            descriptor: SubConnectionDescriptor,
            connection: SubConnection,
    ) {
        if (descriptor.blockchainRid == null) {
            queryConnection?.close()

            logger.debug("Lost query connection to master")
            scheduleQueryReconnection()
        } else {
            val brid = descriptor.blockchainRid
            logger.info { "${logger(descriptor)}: Master node disconnected: blockchainRid = ${brid.toShortHex()}" }

            val chain = chains.get(brid)
            if (chain == null) {
                logger.warn("${logger(descriptor)}: Master chain not found by blockchainRid: ${brid.toShortHex()}")
                connection.close()
            } else {
                if (chain.getConnection() !== connection) {
                    logger.warn("${logger(descriptor)}: Unknown master connection detected for blockchainRid: ${brid.toShortHex()}")
                    connection.close()
                }

                if (chain.getConnection() != null) {
                    logger.info("${logger(descriptor)}: Master connection will be closed for blockchainRid: ${brid.toShortHex()}")
                }
                chain.removeAndCloseConnection()

                // Schedule reconnection to the Master
                scheduleReconnection(chain)
            }
        }
    }

    // ----------------------------------
    //  [SubConnectionManager] impl
    // ----------------------------------

    override fun preAddMsMessageHandler(chainId: Long, handler: MsMessageHandler) {
        preAddedMsMessageHandlers.computeIfAbsent(chainId) { mutableListOf() }.add(handler)
    }

    override fun sendMessageToMaster(chainId: Long, message: MsMessage): Boolean {
        val chain = chains.getOrThrow(chainId)
        return if (chain.isConnected()) {
            chain.getConnection()!!.sendPacket { MsCodec.encode(message) }
            true
        } else {
            logger.error("${logger(chain)}: Can't send packet b/c no connection to master node for chainId=${chain.log()}")
            false
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
    private fun scheduleReconnection(chain: ChainWithOneMasterConnection) {
        logger.debug { "${logger(chain)}: ---------- BEGIN ----------" }
        val brid = chain.config.blockchainRid.toShortHex()
        if (reconnectionScheduledForChain[chain.config.blockchainRid]?.isDone != false) {
            logger.info("${logger(chain)}: Reconnecting in $reconnectDelay to master node: blockchainRid: $brid")
            reconnectionScheduledForChain[chain.config.blockchainRid] = reconnectionExecutor.schedule({
                logger.info("${logger(chain)}: Reconnecting to master node: blockchainRid: $brid")
                connectToMaster(chain)
            }, reconnectDelay.toMillis(), TimeUnit.MILLISECONDS)
        } else {
            logger.debug("${logger(chain)}: Reconnection is already scheduled: blockchainRid: $brid")
        }
        logger.debug { "${logger(chain)}: ---------- END ----------" }
    }

    private fun scheduleQueryReconnection() {
        if (reconnectionScheduledForQuery?.isDone != false) {
            logger.info("Reconnecting in $reconnectDelay to master node")
            reconnectionScheduledForQuery = reconnectionExecutor.schedule({
                logger.info("Reconnecting to master node")
                subConnector.connectMaster(masterNodePeerInfo, queryConnectionDescriptor)
            }, reconnectDelay.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    private fun loggerPrefix(blockchainRid: BlockchainRid): String =
            BlockchainProcessName(appConfig.pubKey, blockchainRid).toString()

    private fun logger(descriptor: SubConnectionDescriptor): String = descriptor.blockchainRid?.let { loggerPrefix(it) }
            ?: ""

    private fun logger(chain: ChainWithOneMasterConnection): String = loggerPrefix(chain.config.blockchainRid)
}
