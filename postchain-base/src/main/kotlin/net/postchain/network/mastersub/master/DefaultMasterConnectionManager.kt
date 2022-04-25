package net.postchain.network.mastersub.master

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.common.BlockchainRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.common.ChainsWithOneConnection
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.master.netty.NettyMasterConnector
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsMessage

/**
 * Enables the master node to pass on messages to one sub-node.
 */
class DefaultMasterConnectionManager(
        val appConfig: AppConfig,
        private val containerNodeConfig: ContainerNodeConfig
) : MasterConnectionManager, MasterConnectorEvents {

    companion object : KLogging()

    var isShutDown = false

    // Here we don't bother with factory
    private val masterConnector = NettyMasterConnector(this).apply {
        init(containerNodeConfig.masterPort)
    }

    private val chainsWithOneSubConnection = ChainsWithOneConnection<
            MsMessageHandler,
            MasterConnection,
            ChainWithOneSubConnection>()

    @Synchronized
    override fun connectSubChain(processName: BlockchainProcessName, subChainConfig: SubChainConfig) {
        val logMsg = subChainConfig.log()
        logger.debug { "$processName: connectSubChain() - Connecting subnode chain: $logMsg" }

        if (isShutDown) {
            logger.warn("$processName: connectSubChain() - Already shut down: connecting subnode chains is " +
                    "not possible. $logMsg")
        } else {
            if (chainsWithOneSubConnection.hasChain(subChainConfig.blockchainRid)) {
                // TODO: Olle: This needs some explanation. Why do we "disconnect" and then "connect" on an existing connection? Do we expect it to be stale?
                logger.info("$processName: connectSubChain() - This chain is already connected, disconnecting " +
                        "old sub-node connection first. $logMsg")
                disconnectSubChain(processName, subChainConfig.chainId)
            }
            chainsWithOneSubConnection.add(ChainWithOneSubConnection(subChainConfig))
            logger.debug { "$processName: connectSubChain() - Subnode chain connected: $logMsg" }
        }
    }

    @Synchronized
    override fun sendPacketToSub(message: MsMessage) {
        logger.debug { "sendPacketToSub() - begin, type: ${message.type}" }
        val bcRid = BlockchainRid(message.blockchainRid)
        val chain = chainsWithOneSubConnection.get(bcRid)
        if (chain != null) {
            val conn = chain.getConnection()
            if (conn != null) {
                conn.sendPacket { MsCodec.encode(message) }
                logger.trace { "sendPacketToSub() - end: message sent" }
            } else {
                logger.debug { "sendPacketToSub() - end: conn not found" }
            }
        } else {
            logger.debug { "sendPacketToSub() - end: chain not found: ${bcRid.toShortHex()}" }
        }
    }

    @Synchronized
    override fun disconnectSubChain(processName: BlockchainProcessName, chainId: Long) {
        logger.debug { "$processName: Disconnecting subnode chain: $chainId" }

        val chain = chainsWithOneSubConnection.get(chainId)
        if (chain != null) {
            chainsWithOneSubConnection.removeAndClose(chain.config.blockchainRid)
            logger.debug { "$processName: Subnode chain disconnected: $chainId" }
        } else {
            logger.debug { "$processName: Subnode chain is not connected: $chainId" }
        }
    }

    @Synchronized
    override fun onSubConnected(
        descriptor: MasterConnectionDescriptor,
        connection: MasterConnection
    ): MsMessageHandler? {
        val processName = buildProcessName(descriptor)
        logger.info { "$processName: Subnode connected: blockchainRid: ${descriptor.blockchainRid.toShortHex()}" }

        val chain = chainsWithOneSubConnection.get(descriptor.blockchainRid)
        return when {
            chain == null -> {
                logger.warn("$processName: Sub chain not found by blockchainRid = ${descriptor.blockchainRid}")
                connection.close()
                null
            }
            chain.getConnection() != null -> {
                logger.debug { "$processName: Subnode already connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                chain.closeConnection() // Close old connection here and store a new one
                chain.setConnection(connection)
                chain.config.messageHandler
            }
            else -> {
                logger.debug { "$processName: Subnode connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                chain.setConnection(connection)
                chain.config.messageHandler
            }
        }
    }

    @Synchronized
    override fun onSubDisconnected(
        descriptor: MasterConnectionDescriptor,
        connection: MasterConnection
    ) {
        val processName = buildProcessName(descriptor)
        logger.debug { "$processName: Subnode disconnected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }

        val chain = chainsWithOneSubConnection.get(descriptor.blockchainRid)
        if (chain == null) {
            connection.close()
            logger.warn("$processName: Subnode chain not found by blockchainRid = ${descriptor.blockchainRid.toShortHex()}")
        } else {
            if (chain.getConnection() !== connection) { // Don't get it, is this expected?
                connection.close()
            }
            chain.removeAndCloseConnection()
        }
    }

    override fun shutdown() {
        synchronized(this) {
            if (isShutDown) return
            isShutDown = true
            chainsWithOneSubConnection.removeAllAndClose()
        }
        // The shutdown is intentionally called outside the sync-block to avoid deadlock
        masterConnector.shutdown()
    }

    private fun buildProcessName(descriptor: MasterConnectionDescriptor): String = BlockchainProcessName(
        appConfig.pubKey, descriptor.blockchainRid
    ).toString()
}