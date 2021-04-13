package net.postchain.network.masterslave.master

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.common.toHex
import net.postchain.config.node.NodeConfig
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.masterslave.protocol.MsDataMessage
import net.postchain.network.masterslave.protocol.MsHandshakeMessage
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.PeersCommConfigFactory
import net.postchain.network.x.XChainPeersConfiguration
import net.postchain.network.x.XPeerID

class DefaultMasterCommunicationManager(
        val nodeConfig: NodeConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        private val peersCommConfigFactory: PeersCommConfigFactory,
        private val masterConnectionManager: MasterConnectionManager,
        private val processName: BlockchainProcessName
) : MasterCommunicationManager {

    companion object : KLogging()

    override fun init() {
        val slaveChainConfig = SlaveChainConfig(chainId, blockchainRid, ::consumeSlavePacket)
        masterConnectionManager.connectSlaveChain(processName, slaveChainConfig)
    }

    private fun consumeSlavePacket(message: MsMessage) {
        logger.trace("${process()}: Receiving a message from slave: blockchainRid = ${blockchainRid.toShortHex()}")

        when (message) {
            is MsHandshakeMessage -> {
                disconnectChainPeers()
                connectChainPeers(message.peers)
            }

            is MsDataMessage -> {
                masterConnectionManager.sendPacket(
                        { message.payload },
                        chainId,
                        XPeerID(message.destination)
                )
            }
        }
    }

    private fun connectChainPeers(peers: List<ByteArray>) {
        logger.info { "${process()}: Connecting chain peers" }

        val peersCommConfig = peersCommConfigFactory.create(nodeConfig, blockchainRid, peers)
        val peersConfig = XChainPeersConfiguration(chainId, blockchainRid, peersCommConfig) { data, peerId ->
            consumePacket(peerId, data)
        }

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

        logger.trace("${process()}: Sending the packet from peer: ${peerId.byteArray.toHex()} " +
                "to slave node: blockchainRid: ${blockchainRid.toShortHex()} ")
        masterConnectionManager.sendPacketToSlave(message)
    }

    override fun shutdown() {
        masterConnectionManager.disconnectChain(chainId) { processName.toString() }
    }

    private fun process(): String {
        return processName.toString()
    }
}