package net.postchain.network.mastersub.master

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.config.node.NodeConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.*
import net.postchain.network.peer.PeerPacketHandler
import net.postchain.network.peer.PeersCommConfigFactory
import net.postchain.network.peer.XChainPeersConfiguration
import java.util.*

/**
 * Manages communication for the give chain
 *
 * For "masters" this means communicate with all peers in a normal fashion, but instead of really process
 * the messages ourselves we wrap them in [MsMessage] and pass them on to the correct sub-node.
 */
open class DefaultMasterCommunicationManager(
        val nodeConfig: NodeConfig,
        private val containerNodeConfig: ContainerNodeConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val peersCommConfigFactory: PeersCommConfigFactory,
        private val connectionManager: ConnectionManager,
        private val masterConnectionManager: MasterConnectionManager,
        private val dataSource: DirectoryDataSource,
        private val processName: BlockchainProcessName
) : AbstractMasterCommunicationManager() {

    companion object : KLogging()

    private lateinit var sendConnectedPeersTask: TimerTask

    override fun init() {
        val subnodeChainConfig = SubChainConfig(chainId, blockchainRid, subnodePacketConsumer())
        masterConnectionManager.connectSubChain(processName, subnodeChainConfig)

        // Scheduling SendConnectedPeers task
        sendConnectedPeersTask = scheduleTask(containerNodeConfig.containerSendConnectedPeersPeriod) {
            val peers = connectionManager.getConnectedNodes(chainId)
            val msg = MsConnectedPeersMessage(blockchainRid.data, peers.map { it.byteArray })
            masterConnectionManager.sendPacketToSub(msg)
        }
    }

    override fun sendHeartbeatToSub(heartbeatEvent: HeartbeatEvent) {
        logger.trace {
            "${process()}: Sending a heartbeat packet to subnode: blockchainRid: " +
                    "${blockchainRid.toShortHex()} "
        }
        val message = MsHeartbeatMessage(blockchainRid.data, heartbeatEvent.timestamp)
        masterConnectionManager.sendPacketToSub(message)
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
                        val nextHeight = dataSource.findNextConfigurationHeight(message.blockchainRid, message.currentHeight)
                        val config = if (nextHeight == null) {
                            null
                        } else {
                            if (message.nextHeight == null || nextHeight != message.nextHeight) {
                                dataSource.getConfiguration(message.blockchainRid, nextHeight)
                            } else {
                                null // message.nextHeight != null && nextHeight == message.nextHeight
                            }
                        }

                        val response = MsNextBlockchainConfigMessage(message.blockchainRid, nextHeight, config)
                        masterConnectionManager.sendPacketToSub(response)
                        logger.debug {
                            "${process()}: BlockchainConfig sent to subnode: blockchainRid: " +
                                    "${blockchainRid.toShortHex()}, nextHeight: $nextHeight, config size: " +
                                    "${config?.size}"
                        }
                    }

                    is MsSubnodeStatusMessage -> {
                        logger.debug {
                            "${process()}: Subnode status: blockchainRid: " +
                                    "${BlockchainRid(message.blockchainRid).toShortHex()}, height: ${message.height}"
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
        logger.info { "${process()}: Connecting chain peers" }

        val peersCommConfig = peersCommConfigFactory.create(nodeConfig, blockchainRid, peers, null)

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
        logger.trace { "${process()}: consumePacket() - Receiving a packet from peer: ${nodeId.byteArray.toHex()}" }

        val message = MsDataMessage(
                blockchainRid.data,
                nodeId.byteArray, // sender
                nodeConfig.pubKeyByteArray, // Can be omitted?
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
            sendConnectedPeersTask.cancel()
        }

        val prefixFun: () -> String = { processName.toString() }
        connectionManager.disconnectChain(prefixFun, chainId)
    }

    private fun process(): String {
        return processName.toString()
    }
}