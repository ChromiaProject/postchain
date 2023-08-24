package net.postchain.network.mastersub.master

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockRid
import net.postchain.core.NodeRid
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsCommittedBlockMessage
import net.postchain.network.mastersub.protocol.MsConnectedPeersMessage
import net.postchain.network.mastersub.protocol.MsDataMessage
import net.postchain.network.mastersub.protocol.MsHandshakeMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryResponse
import net.postchain.network.peer.PeerPacketHandler
import net.postchain.network.peer.PeersCommConfigFactory
import net.postchain.network.peer.XChainPeersConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
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
        private val afterSubnodeCommitListeners: Set<AfterSubnodeCommitListener>
) : MasterCommunicationManager {

    companion object : KLogging() {
        private val peerTaskScheduler = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder().setNameFormat("PeerTaskScheduler").build())
    }

    private lateinit var sendConnectedPeersTask: ScheduledFuture<*>

    override fun init() {
        val subnodeChainConfig = SubChainConfig(chainId, blockchainRid, subnodePacketConsumer())
        masterConnectionManager.initSubChainConnection(subnodeChainConfig)

        // Scheduling SendConnectedPeers task
        sendConnectedPeersTask = peerTaskScheduler.scheduleAtFixedRate({
            withLoggingContext(
                    BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                    CHAIN_IID_TAG to chainId.toString()
            ) {
                val peers = connectionManager.getConnectedNodes(chainId)
                val msg = MsConnectedPeersMessage(blockchainRid.data, peers.map { it.data })
                masterConnectionManager.sendPacketToSub(blockchainRid, msg)
            }
        }, 0, containerNodeConfig.sendMasterConnectedPeersPeriod, TimeUnit.MILLISECONDS)
    }

    private fun subnodePacketConsumer(): MsMessageHandler {
        return object : MsMessageHandler {
            override fun onMessage(message: MsMessage) {
                withLoggingContext(
                        BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                        CHAIN_IID_TAG to chainId.toString()
                ) {
                    logger.trace {
                        "Receiving a message ${message.javaClass.simpleName} from subnode"
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

                        is MsQueryResponse, is MsBlockAtHeightResponse, is MsQueryFailure -> {
                            masterConnectionManager.masterSubQueryManager.onMessage(message)
                        }
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
        logger.info("Connecting chain peers")

        val peersCommConfig = peersCommConfigFactory.create(appConfig, nodeConfig, chainId, blockchainRid, peers, null)

        val packetHandler = object : PeerPacketHandler {
            override fun handle(data: ByteArray, nodeId: NodeRid) {
                withLoggingContext(
                        BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                        CHAIN_IID_TAG to chainId.toString()
                ) {
                    consumePacket(nodeId, data)
                }
            }
        }
        val peersConfig = XChainPeersConfiguration(chainId, blockchainRid, peersCommConfig, packetHandler)

        connectionManager.connectChain(peersConfig, true)
    }

    private fun disconnectChainPeers() {
        logger.info("Disconnecting chain peers")
        connectionManager.disconnectChain(chainId)
    }

    /**
     * Wraps the original message into a [MsMessage] and sends it to the sub-node.
     *
     * @param nodeId is the sender of the message
     * @param packet is the message data
     */
    private fun consumePacket(nodeId: NodeRid, packet: ByteArray) {
        logger.trace { "consumePacket() - Receiving a packet from peer: ${nodeId.toHex()}" }

        val message = MsDataMessage(
                nodeId.data, // sender
                appConfig.pubKeyByteArray, // Can be omitted?
                packet
        )

        masterConnectionManager.sendPacketToSub(blockchainRid, message)

        logger.trace("consumePacket() - end")
    }

    override fun shutdown() {
        // Canceling SendConnectedPeers task
        if (::sendConnectedPeersTask.isInitialized) {
            sendConnectedPeersTask.cancel(true)
        }

        connectionManager.disconnectChain(chainId)
        masterConnectionManager.disconnectSubChain(chainId)
    }
}