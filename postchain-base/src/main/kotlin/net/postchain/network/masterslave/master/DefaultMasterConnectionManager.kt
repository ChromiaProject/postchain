package net.postchain.network.masterslave.master

import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import net.postchain.network.masterslave.MsConnection
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.master.netty.NettyMasterConnector
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.DefaultXConnectionManager
import net.postchain.network.x.XConnectorFactory

class DefaultMasterConnectionManager<PacketType>(
        connectorFactory: XConnectorFactory<PacketType>,
        packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        val nodeConfig: NodeConfig
) : DefaultXConnectionManager<PacketType>(
        connectorFactory,
        packetEncoderFactory,
        packetDecoderFactory
), MasterConnectionManager, MasterConnectorEvents {

    private class SlaveChain(val config: SlaveChainConfig) {
        var connection: MsConnection? = null
    }

    private val masterConnector = NettyMasterConnector(this).apply {
        init(nodeConfig.masterPort)
    }

    private val slaveChains: MutableMap<BlockchainRid, SlaveChain> = mutableMapOf()

    @Synchronized
    override fun connectSlaveChain(processName: BlockchainProcessName, slaveChainConfig: SlaveChainConfig) {
        val logMsg = "chainId: ${slaveChainConfig.chainId}, blockchainRid: ${slaveChainConfig.blockchainRid.toShortHex()}"
        logger.debug { "$processName: Connecting slave chain: $logMsg" }

        if (isShutDown) {
            logger.warn { "$processName: Already shut down: connecting slave chains is not possible" }
        } else {
            if (slaveChainConfig.blockchainRid in slaveChains) {
                disconnectSlaveChain(processName, slaveChainConfig.chainId)
            }
            slaveChains[slaveChainConfig.blockchainRid] = SlaveChain(slaveChainConfig)
            logger.debug { "$processName: Slave chain connected: $logMsg" }
        }
    }

    @Synchronized
    override fun sendPacketToSlave(message: MsMessage) {
        slaveChains[BlockchainRid(message.blockchainRid)]
                ?.connection?.sendPacket { MsCodec.encode(message) }
    }

    @Synchronized
    override fun disconnectSlaveChain(processName: BlockchainProcessName, chainId: Long) {
        logger.debug { "$processName: Disconnecting slave chain: $chainId" }

        val chain = slaveChains.values.firstOrNull { it.config.chainId == chainId }
        if (chain != null) {
            chain.connection?.close()
            chain.connection = null
            slaveChains.remove(chain.config.blockchainRid)
            logger.debug { "$processName: Slave chain disconnected: $chainId" }
        } else {
            logger.debug { "$processName: Slave chain is not connected: $chainId" }
        }
    }

    @Synchronized
    override fun onSlaveConnected(descriptor: MasterConnectionDescriptor, connection: MsConnection): MsMessageHandler? {
        val processName = buildProcessName(descriptor)
        logger.info { "$processName: Slave node connected: blockchainRid: ${descriptor.blockchainRid}" }

        val chain = slaveChains[descriptor.blockchainRid]
        return when {
            chain == null -> {
                logger.warn("$processName: Slave chain not found by blockchainRid = ${descriptor.blockchainRid}")
                connection.close()
                null
            }
            chain.connection != null -> {
                logger.debug { "$processName: Slave node already connected: blockchainRid = ${descriptor.blockchainRid}" }
                chain.connection?.close() // Close old connection here and store a new one
                chain.connection = connection
                chain.config.messageHandler
            }
            else -> {
                logger.debug { "$processName: Slave node connected: blockchainRid = ${descriptor.blockchainRid}" }
                chain.connection = connection
                chain.config.messageHandler
            }
        }
    }

    @Synchronized
    override fun onSlaveDisconnected(descriptor: MasterConnectionDescriptor, connection: MsConnection) {
        val processName = buildProcessName(descriptor)
        logger.debug { "$processName: Slave node disconnected: blockchainRid = ${descriptor.blockchainRid}" }

        val chain = slaveChains[descriptor.blockchainRid]
        if (chain == null) {
            connection.close()
            logger.warn("$processName: Slave chain not found by blockchainRid = ${descriptor.blockchainRid}")
        } else {
            if (chain.connection !== connection) {
                connection.close()
            }
            chain.connection?.close()
            chain.connection = null
        }
    }

    @Synchronized
    override fun shutdown() {
        super.shutdown()

        slaveChains.values.forEach { it.connection?.close() }
        slaveChains.clear()

        masterConnector.shutdown()
    }

    private fun buildProcessName(descriptor: MasterConnectionDescriptor): String = BlockchainProcessName(
            nodeConfig.pubKey, descriptor.blockchainRid
    ).toString()
}