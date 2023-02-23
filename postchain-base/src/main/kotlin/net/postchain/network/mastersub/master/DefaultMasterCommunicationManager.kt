package net.postchain.network.mastersub.master

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.containers.bpm.bcconfig.BlockchainConfigVerifier
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockRid
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsCommittedBlockMessage
import net.postchain.network.mastersub.protocol.MsConnectedPeersMessage
import net.postchain.network.mastersub.protocol.MsDataMessage
import net.postchain.network.mastersub.protocol.MsFindNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsHandshakeMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import net.postchain.network.peer.PeerPacketHandler
import net.postchain.network.peer.PeersCommConfigFactory
import net.postchain.network.peer.XChainPeersConfiguration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Manages communication for the give chain
 *
 * For "masters" this means to communicate with all peers in a normal fashion, but instead of really process
 * the messages ourselves we wrap them in [MsMessage] and pass them on to the correct sub-node.
 */
open class DefaultMasterCommunicationManager(
        val appConfig: AppConfig,
        val nodeConfig: NodeConfig,
        private val containerNodeConfig: ContainerNodeConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val peersCommConfigFactory: PeersCommConfigFactory,
        private val connectionManager: ConnectionManager,
        private val masterConnectionManager: MasterConnectionManager,
        private val dataSource: DirectoryDataSource,
        private val processName: BlockchainProcessName,
        private val afterSubnodeCommitListeners: Set<AfterSubnodeCommitListener>,
        private val blockQueriesProvider: BlockQueriesProvider,
) : AbstractMasterCommunicationManager() {

    companion object : KLogging()

    private lateinit var sendConnectedPeersTask: ScheduledFuture<*>
    private val configVerifier = BlockchainConfigVerifier(appConfig)

    override fun init() {
        val subnodeChainConfig = SubChainConfig(chainId, blockchainRid, subnodePacketConsumer())
        masterConnectionManager.initSubChainConnection(processName, subnodeChainConfig)

        // Scheduling SendConnectedPeers task
        sendConnectedPeersTask = ScheduledThreadPoolExecutor(1).scheduleAtFixedRate({
            val peers = connectionManager.getConnectedNodes(chainId)
            val msg = MsConnectedPeersMessage(blockchainRid.data, peers.map { it.data })
            masterConnectionManager.sendPacketToSub(msg)
        }, 0, containerNodeConfig.sendMasterConnectedPeersPeriod, TimeUnit.MILLISECONDS)
    }

    fun subnodePacketConsumer(): MsMessageHandler {
        return object : MsMessageHandler {
            override fun onMessage(message: MsMessage) {
                logger.trace {
                    "${process()}: Receiving a message ${message.javaClass.simpleName} from subnode: " +
                            "blockchainRid = ${blockchainRid.toShortHex()}"
                }

                when (message) {
                    is MsHandshakeMessage -> {
                        disconnectChainPeers()
                        connectChainPeers(message.peers)
                    }

                    is MsDataMessage -> {
                        connectionManager.sendPacket(
                                { message.xPacket },
                                chainId,
                                NodeRid(message.destination)
                        )
                    }

                    is MsFindNextBlockchainConfigMessage -> {
                        logger.debug {
                            "${process()}: BlockchainConfig requested by subnode: blockchainRid: " + blockchainRid.toShortHex()
                        }
                        val nextHeight = dataSource.findNextConfigurationHeight(message.blockchainRid, message.lastHeight)
                        val config = if (nextHeight == null) {
                            null
                        } else {
                            if (message.nextHeight == null || nextHeight != message.nextHeight) {
                                dataSource.getConfiguration(message.blockchainRid, nextHeight)
                            } else {
                                null // message.nextHeight != null && nextHeight == message.nextHeight
                            }
                        }
                        val hash = config?.let { configVerifier.calculateHash(it) }
                        val hashStr = hash?.let { BlockchainRid(it).toHex() }

                        val response = MsNextBlockchainConfigMessage(message.blockchainRid, message.lastHeight, nextHeight, config, hash)
                        masterConnectionManager.sendPacketToSub(response)
                        logger.debug {
                            "${process()}: BlockchainConfig sent to subnode: blockchainRid: " +
                                    "${blockchainRid.toShortHex()}, nextHeight: $nextHeight, config size: " +
                                    "${config?.size}, config hash: $hashStr"
                        }
                    }

                    is MsCommittedBlockMessage -> {
                        afterSubnodeCommitListeners.forEach {
                            it.onAfterCommitInSubnode(
                                    BlockchainRid(message.blockchainRid),
                                    BlockRid(message.blockRid),
                                    blockHeader = message.blockHeader,
                                    witnessData = message.witnessData
                            )
                        }
                    }

                    is MsQueryRequest -> {
                        if (message.targetBlockchainRid == null) {
                            try {
                                val response = dataSource.query(message.name, message.args)
                                masterConnectionManager.sendPacketToSub(MsQueryResponse(
                                        message.blockchainRid,
                                        message.requestId,
                                        response
                                ))
                            } catch (e: Exception) {
                                masterConnectionManager.sendPacketToSub(MsQueryFailure(
                                        message.blockchainRid,
                                        message.requestId,
                                        e.toString()
                                ))
                            }
                        } else {
                            val blockQueries = blockQueriesProvider.getBlockQueries(message.targetBlockchainRid)
                            if (blockQueries != null) {
                                blockQueries.query(message.name, message.args).whenCompleteUnwrapped { response, exception ->
                                    if (exception == null) {
                                        masterConnectionManager.sendPacketToSub(MsQueryResponse(
                                                message.blockchainRid,
                                                message.requestId,
                                                response
                                        ))
                                    } else {
                                        masterConnectionManager.sendPacketToSub(MsQueryFailure(
                                                message.blockchainRid,
                                                message.requestId,
                                                exception.toString()
                                        ))
                                    }
                                }
                            } else {
                                logger.trace { "Forwarding message to subnode with target blockchain-rid ${message.targetBlockchainRid}, message blockchain-rid ${message.blockchainRid.toHex()} and request id ${message.requestId}" }
                                masterConnectionManager.masterSubQueryManager.query(
                                        chainId,
                                        message.targetBlockchainRid,
                                        message.targetBlockchainRid,
                                        message.name,
                                        message.args
                                ).whenCompleteUnwrapped { response, error ->
                                    if (error == null) {
                                        logger.trace { "Got response from subnode with target blockchain-rid ${message.targetBlockchainRid}, message blockchain-rid ${message.blockchainRid.toHex()} and request id ${message.requestId}" }
                                        masterConnectionManager.sendPacketToSub(MsQueryResponse(
                                                message.blockchainRid,
                                                message.requestId,
                                                response
                                        ))
                                    } else {
                                        logger.trace { "Failed to forward request with target blockchain-rid ${message.targetBlockchainRid}, message blockchain-rid ${message.blockchainRid.toHex()} and request id ${message.requestId} to subnode, error: ${error.message}" }
                                        masterConnectionManager.sendPacketToSub(MsQueryFailure(
                                                message.blockchainRid,
                                                message.requestId,
                                                error.toString()
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    is MsBlockAtHeightRequest -> {
                        val blockQueries = blockQueriesProvider.getBlockQueries(message.targetBlockchainRid)
                        if (blockQueries != null) {
                            blockQueries.getBlockRid(message.height).thenCompose {
                                if (it == null) {
                                    CompletableFuture.completedFuture(null)
                                } else {
                                    blockQueries.getBlock(it, true)
                                }
                            }.whenCompleteUnwrapped { response, exception ->
                                if (exception == null) {
                                    masterConnectionManager.sendPacketToSub(MsBlockAtHeightResponse(
                                            message.blockchainRid,
                                            message.requestId,
                                            response
                                    ))
                                } else {
                                    masterConnectionManager.sendPacketToSub(MsQueryFailure(
                                            message.blockchainRid,
                                            message.requestId,
                                            exception.toString()
                                    ))
                                }
                            }
                        } else {
                            masterConnectionManager.masterSubQueryManager.blockAtHeight(
                                    chainId,
                                    message.targetBlockchainRid,
                                    message.targetBlockchainRid,
                                    message.height
                            ).whenCompleteUnwrapped { response, error ->
                                if (error == null) {
                                    masterConnectionManager.sendPacketToSub(MsBlockAtHeightResponse(
                                            message.blockchainRid,
                                            message.requestId,
                                            response
                                    ))
                                } else {
                                    masterConnectionManager.sendPacketToSub(MsQueryFailure(
                                            message.blockchainRid,
                                            message.requestId,
                                            error.toString()
                                    ))
                                }
                            }
                        }
                    }

                    is MsQueryResponse, is MsBlockAtHeightResponse, is MsQueryFailure -> {
                        masterConnectionManager.masterSubQueryManager.onMessage(message)
                    }
                }
            }
        }
    }

    /**
     * Will connect the chain to all the network peers.
     *
     * @param peers all "normal" peers in our network
     */
    private fun connectChainPeers(peers: List<ByteArray>) {
        logger.info { "${process()}: Connecting chain peers" }

        val peersCommConfig = peersCommConfigFactory.create(appConfig, nodeConfig, blockchainRid, peers, null)

        val packetHandler = object : PeerPacketHandler {
            override fun handle(data: ByteArray, nodeId: NodeRid) {
                consumePacket(nodeId, data)
            }
        }
        val peersConfig = XChainPeersConfiguration(chainId, blockchainRid, peersCommConfig, packetHandler)

        connectionManager.connectChain(peersConfig, true) { processName.toString() }
    }

    private fun disconnectChainPeers() {
        logger.info { "${process()}: Disconnecting chain peers" }
        val prefixFun: () -> String = { processName.toString() }
        connectionManager.disconnectChain(prefixFun, chainId)
    }

    /**
     * Wraps the original message into a [MsMessage] and sends it to the sub-node.
     *
     * @param nodeId is the sender of the message
     * @param packet is the message data
     */
    private fun consumePacket(nodeId: NodeRid, packet: ByteArray) {
        logger.trace { "${process()}: consumePacket() - Receiving a packet from peer: ${nodeId.toHex()}" }

        val message = MsDataMessage(
                blockchainRid.data,
                nodeId.data, // sender
                appConfig.pubKeyByteArray, // Can be omitted?
                packet
        )

        logger.trace {
            "${process()}: Sending a brid ${BlockchainRid(message.blockchainRid).toShortHex()} packet " +
                    "from peer: ${message.source.toHex()} to subnode: ${message.destination.toHex()} "
        }
        masterConnectionManager.sendPacketToSub(message)

        logger.trace { "${process()}: consumePacket() - end" }
    }

    override fun shutdown() {
        // Canceling SendConnectedPeers task
        if (::sendConnectedPeersTask.isInitialized) {
            sendConnectedPeersTask.cancel(true)
        }

        val prefixFun: () -> String = { processName.toString() }
        connectionManager.disconnectChain(prefixFun, chainId)
        masterConnectionManager.disconnectSubChain(processName, chainId)
    }

    private fun process(): String {
        return processName.toString()
    }
}