package net.postchain.network.masterslave.master

import net.postchain.base.BlockchainRid
import net.postchain.base.CryptoSystem
import net.postchain.base.PeerCommConfiguration
import net.postchain.core.ProgrammerMistake
import net.postchain.core.byteArrayKeyOf
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import net.postchain.network.masterslave.NodeConnection
import net.postchain.network.masterslave.PacketHandler
import net.postchain.network.masterslave.master.netty.NettyMasterConnector
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.DefaultPeersConnectionStrategy
import net.postchain.network.x.DefaultXConnectionManager
import net.postchain.network.x.PeersConnectionStrategy
import net.postchain.network.x.XConnectorFactory

class DefaultMasterConnectionManager<PacketType>(
        connectorFactory: XConnectorFactory<PacketType>,
        peerCommConfiguration: PeerCommConfiguration,
        packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        cryptoSystem: CryptoSystem,
        peersConnectionStrategy: PeersConnectionStrategy = DefaultPeersConnectionStrategy
) : DefaultXConnectionManager<PacketType>(
        connectorFactory,
        peerCommConfiguration,
        packetEncoderFactory,
        packetDecoderFactory,
        cryptoSystem,
        peersConnectionStrategy
), MasterConnectionManager, MasterConnectorEvents {

    protected class SlaveChain(config: SlaveChainConfiguration) {
        val chainId = config.chainId
        val blockchainRid = config.blockchainRid
        val packetHandler = config.packetHandler
        var connection: NodeConnection? = null
    }

    // TODO: [POS-129]: Extract to factory
    private val masterConnector = NettyMasterConnector<PacketType>(this) // Runs Netty Server
    protected val slaveChains: MutableMap<BlockchainRid, SlaveChain> = mutableMapOf()

    @Synchronized
    override fun connectSlaveChain(slaveChainConfig: SlaveChainConfiguration, loggingPrefix: () -> String) {
        val chainStr = "chainId: ${slaveChainConfig.chainId}, blockchainRid: ${slaveChainConfig.blockchainRid.toShortHex()}"
        logger.debug { "${loggingPrefix()}: Connecting slave chain: $chainStr" }

        if (isShutDown) throw ProgrammerMistake("Already shut down: connecting slave chains is not possible")
        if (slaveChainConfig.blockchainRid in slaveChains) {
            disconnectSlaveChain(slaveChainConfig.chainId, loggingPrefix)
        }
        slaveChains[slaveChainConfig.blockchainRid] = SlaveChain(slaveChainConfig)

        logger.debug { "${logger(slaveChainConfig)}: Slave chain connected: $chainStr" }
    }

    @Synchronized
    override fun sendPacketToSlave(message: MsMessage) {
        slaveChains[BlockchainRid(message.blockchainRid)]
                ?.connection?.sendPacket { MsCodec.encode(message) }
    }

    @Synchronized
    override fun disconnectSlaveChain(chainId: Long, loggingPrefix: () -> String) {
        logger.debug { "${loggingPrefix()}: Disconnecting slave chain: $chainId" }

        val chain = slaveChains.values.firstOrNull { it.chainId == chainId }
        if (chain != null) {
            chain.connection?.close()
            chain.connection = null
            slaveChains.remove(chain.blockchainRid)
            logger.debug { "${loggingPrefix()}: Slave chain disconnected: $chainId" }
        } else {
            logger.debug { "${loggingPrefix()}: Slave chain is not connected: $chainId" }
        }
    }

    @Synchronized
    override fun onSlaveConnected(descriptor: MasterConnectionDescriptor, connection: NodeConnection): PacketHandler? {
        logger.info { "${logger(descriptor)}: Slave node connected: blockchainRid: ${descriptor.blockchainRid}" }

        val chain = slaveChains[descriptor.blockchainRid]
        return when {
            chain == null -> {
                logger.warn("${logger(descriptor)}: Slave chain not found by blockchainRid = ${descriptor.blockchainRid}")
                connection.close()
                null
            }
            chain.connection != null -> {
                logger.debug { "${logger(descriptor)}: Slave node already connected: blockchainRid = ${descriptor.blockchainRid}" }
                chain.connection?.close() // Close old connection here and store a new one
                chain.connection = connection
                chain.packetHandler
            }
            else -> {
                logger.debug { "${logger(descriptor)}: Slave node connected: blockchainRid = ${descriptor.blockchainRid}" }
                chain.connection = connection
                chain.packetHandler
            }
        }
    }

    @Synchronized
    override fun onSlaveDisconnected(descriptor: MasterConnectionDescriptor, connection: NodeConnection) {
        logger.debug { "${logger(descriptor)}: Slave node disconnected: blockchainRid = ${descriptor.blockchainRid}" }

        val chain = slaveChains[descriptor.blockchainRid]
        if (chain == null) {
            connection.close()
            logger.warn("${logger(descriptor)}: Slave chain not found by blockchainRid = ${descriptor.blockchainRid}")
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

        slaveChains.forEach { (_, chain) -> chain.connection?.close() }
        slaveChains.clear()

        masterConnector.shutdown()
    }

    private fun logger(descriptor: MasterConnectionDescriptor): String = loggingPrefix(descriptor.blockchainRid)

    private fun logger(config: SlaveChainConfiguration): String = loggingPrefix(config.blockchainRid)

    private fun loggingPrefix(blockchainRid: BlockchainRid): String = BlockchainProcessName(
            peerCommConfiguration.myPeerInfo().pubKey.byteArrayKeyOf().toString(),
            blockchainRid
    ).toString()
}