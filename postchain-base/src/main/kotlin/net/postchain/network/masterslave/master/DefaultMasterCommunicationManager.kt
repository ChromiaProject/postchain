package net.postchain.network.masterslave.master

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.common.toHex
import net.postchain.config.node.NodeConfig
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.protocol.*
import net.postchain.network.x.PeersCommConfigFactory
import net.postchain.network.x.XChainPeersConfiguration
import net.postchain.network.x.XPeerID

open class DefaultMasterCommunicationManager(
        val nodeConfig: NodeConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val peersCommConfigFactory: PeersCommConfigFactory,
        private val masterConnectionManager: MasterConnectionManager,
        private val dataSource: DirectoryDataSource,
        private val processName: BlockchainProcessName
) : MasterCommunicationManager {

    companion object : KLogging()

    override fun init() {
        val slaveChainConfig = SlaveChainConfig(chainId, blockchainRid, slavePacketConsumer())
        masterConnectionManager.connectSlaveChain(processName, slaveChainConfig)
    }

    override fun sendHeartbeatToSlave(heartbeatEvent: HeartbeatEvent) {
        logger.trace("${process()}: Sending a heartbeat packet to subnode: blockchainRid: ${blockchainRid.toShortHex()} ")
        val message = MsHeartbeatMessage(blockchainRid.data, heartbeatEvent.timestamp)
        masterConnectionManager.sendPacketToSlave(message)
    }

    fun slavePacketConsumer(): MsMessageHandler {
        return object : MsMessageHandler {
            override fun onMessage(message: MsMessage) {
                logger.debug("${process()}: Receiving a message ${message.javaClass.simpleName} from slave: blockchainRid = ${blockchainRid.toShortHex()}")

                when (message) {
                    is MsHandshakeMessage -> {
                        disconnectChainPeers()
                        connectChainPeers(message.peers)
                    }

                    is MsDataMessage -> {
                        masterConnectionManager.sendPacket(
                                { message.xPacket },
                                chainId,
                                XPeerID(message.destination)
                        )
                    }

                    is MsFindNextBlockchainConfigMessage -> {
                        logger.debug { "${process()}: BlockchainConfig requested by subnode: blockchainRid: ${blockchainRid.toShortHex()}" }
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
                        masterConnectionManager.sendPacketToSlave(response)
                        logger.debug {
                            "${process()}: BlockchainConfig sent to subnode: " +
                                    "blockchainRid: ${blockchainRid.toShortHex()}, " +
                                    "nextHeight: $nextHeight, config size: ${config?.size}"
                        }
                    }

                    is MsSubnodeStatusMessage -> {
                        logger.debug {
                            "${process()}: Subnode status: " +
                                    "blockchainRid: ${BlockchainRid(message.blockchainRid).toShortHex()}, " +
                                    "height: ${message.height}"
                        }
                    }
                }
            }
        }
    }

    private fun connectChainPeers(peers: List<ByteArray>) {
        logger.info { "${process()}: Connecting chain peers" }

        val peersCommConfig = peersCommConfigFactory.create(nodeConfig, blockchainRid, peers, null)
        val packetHandler = { data: ByteArray, peerId: XPeerID -> consumePacket(peerId, data) }
        val peersConfig = XChainPeersConfiguration(chainId, blockchainRid, peersCommConfig, packetHandler)

        masterConnectionManager.connectChain(peersConfig, true) { processName.toString() }
    }

    private fun disconnectChainPeers() {
        logger.info { "${process()}: Disconnecting chain peers" }
        masterConnectionManager.disconnectChain(chainId) { processName.toString() }
    }

    private fun consumePacket(peerId: XPeerID, packet: ByteArray) {
        logger.trace("${process()}: Receiving a packet from peer: ${peerId.byteArray.toHex()}")

        val message = MsDataMessage(
                peerId.byteArray,
                nodeConfig.pubKeyByteArray, // Can be omitted
                blockchainRid.data,
                packet)

        logger.trace(
                "${process()}: Sending the packet from peer: ${peerId.byteArray.toHex()} " +
                        "to subnode: blockchainRid: ${blockchainRid.toShortHex()} ")
        masterConnectionManager.sendPacketToSlave(message)
    }

    override fun shutdown() {
        masterConnectionManager.disconnectChain(chainId) { processName.toString() }
    }

    private fun process(): String {
        return processName.toString()
    }
}