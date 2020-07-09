package net.postchain.network.masterslave.master

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.common.toHex
import net.postchain.config.node.NodeConfig
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.EbftPacketDecoder
import net.postchain.ebft.EbftPacketEncoder
import net.postchain.network.masterslave.protocol.DataMsMessage
import net.postchain.network.masterslave.protocol.HandshakeMsMessage
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.PeersCommunicationConfigFactory
import net.postchain.network.x.XChainPeersConfiguration
import net.postchain.network.x.XPeerID

open class DefaultMasterCommunicationManager<PacketType>(
        val nodeConfig: NodeConfig,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        protected val peersCommunicationConfigFactory: PeersCommunicationConfigFactory,
        protected val masterConnectionManager: MasterConnectionManager,
        protected val processName: BlockchainProcessName
) : MasterCommunicationManager {

    companion object : KLogging()

    override fun init() {
        // Connecting correspondent slave chain to slave node
        val slaveChainConfig = SlaveChainConfiguration(chainId, blockchainRid, ::consumeSlavePacket)
        masterConnectionManager.connectSlaveChain(slaveChainConfig) { processName.toString() }
    }

    // { data: ByteArray, peerID: XPeerID -> consumePacket(peerID, data) },
    // blockchainRid: BlockchainRid, data: ByteArray
    protected open fun consumeSlavePacket(message: MsMessage) {
        logger.trace("${logPrefix()}: Receiving a packet from slave: blockchainRid = ${blockchainRid.toHex()}")

        synchronized(this) {
            when (message) {
                is HandshakeMsMessage -> {
                    disconnectChainPeers()
                    connectChainPeers(message.signers)
                }

                is DataMsMessage -> {
                    logger.trace("${logPrefix()}: Sending the packet to slave node")
                    masterConnectionManager.sendPacket(
                            { message.messageData },
                            chainId,
                            XPeerID(message.destination)
                    )
                }
            }
        }
    }

    private fun connectChainPeers(signers: List<ByteArray>) {
        logger.info { "${logPrefix()}: Connecting chain peers" }

        val communicationConfig = peersCommunicationConfigFactory.create(
                nodeConfig, chainId, blockchainRid, signers)

        val packetEncoder = EbftPacketEncoder(communicationConfig, blockchainRid)
        val packetDecoder = EbftPacketDecoder(communicationConfig)

        val peerConfig = XChainPeersConfiguration(
                chainId,
                blockchainRid,
                communicationConfig,
                { data: ByteArray, peerID: XPeerID -> consumePacket(peerID, data) },
                packetEncoder,
                packetDecoder
        )

        masterConnectionManager.connectChain(peerConfig, true) { processName.toString() }
    }

    private fun disconnectChainPeers() {
        logger.info { "${logPrefix()}: Disconnecting chain peers" }
        masterConnectionManager.disconnectChain(chainId) { processName.toString() }
    }

    // { data: ByteArray, peerID: XPeerID -> consumePacket(peerID, data) },
    // blockchainRid: BlockchainRid, data: ByteArray
    protected fun consumePacket(peerId: XPeerID, packet: ByteArray) {
        logger.trace("${logPrefix()}: Receiving a packet from peer: ${peerId.byteArray.toHex()}")

        val message = DataMsMessage(
                peerId.byteArray,
                nodeConfig.pubKeyByteArray, // It's node required here
                blockchainRid.data,
                packet)

        synchronized(this) {
            logger.trace("${logPrefix()}: Sending the packet from peer: ${peerId.byteArray.toHex()} " +
                    "to slave node: blockchainRid: ${blockchainRid.toShortHex()} ")
            masterConnectionManager.sendPacketToSlave(message)
        }
    }

    override fun shutdown() {
        masterConnectionManager.disconnectChain(chainId) { processName.toString() }
    }

    private fun logPrefix(): String {
        return processName.toString()
    }
}