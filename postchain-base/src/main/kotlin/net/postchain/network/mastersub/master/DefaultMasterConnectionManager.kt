package net.postchain.network.mastersub.master

import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.network.common.ChainsWithOneConnection
import net.postchain.network.mastersub.MasterSubQueryManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.master.netty.NettyMasterConnector
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsMessage

/**
 * Enables the master node to pass on messages to one sub-node.
 */
class DefaultMasterConnectionManager(
        val appConfig: AppConfig,
        containerNodeConfig: ContainerNodeConfig,
        private val blockQueriesProvider: BlockQueriesProvider
) : MasterConnectionManager, MasterConnectorEvents {

    companion object : KLogging()

    override val masterSubQueryManager = MasterSubQueryManager { blockchainRid, message ->
        if (blockchainRid == null) throw ProgrammerMistake("Missing destination chain")
        sendPacketToSub(blockchainRid, message)
    }

    private var isShutDown = false

    // Here we don't bother with factory
    private val masterConnector = NettyMasterConnector(this, containerNodeConfig.masterPort)

    private val chainsWithOneSubConnection = ChainsWithOneConnection<
            MsMessageHandler,
            MasterConnection,
            ChainWithOneSubConnection>()

    override lateinit var dataSource: ManagedNodeDataSource
    private val queryConnections = mutableMapOf<Int, MasterConnection>()

    @Synchronized
    override fun initSubChainConnection(subChainConfig: SubChainConfig) {
        val prefix = "connectSubChain()"
        logger.debug("$prefix - Initializing subnode chain connection")

        if (isShutDown) {
            logger.warn("$prefix - Already shut down: connecting subnode chains is not possible.")
        } else {
            if (chainsWithOneSubConnection.hasChain(subChainConfig.blockchainRid)) {
                logger.warn("$prefix - This chain is already connected, disconnecting old sub-node connection first.")
                disconnectSubChain(subChainConfig.chainId)
            }
            chainsWithOneSubConnection.add(ChainWithOneSubConnection(subChainConfig))
            logger.debug("$prefix - Subnode chain connection initialized")
        }
    }

    @Synchronized
    override fun sendPacketToSub(blockchainRid: BlockchainRid, message: MsMessage): Boolean {
        val prefix = "sendPacketToSub()"
        logger.debug { "$prefix - begin, type: ${message.type}" }
        val chain = chainsWithOneSubConnection.get(blockchainRid)
        return if (chain != null) {
            val conn = chain.getConnection()
            // We should not pass anything on to the subnode until receiving a handshake since
            // packets must be coming from old connections
            if (conn != null && chain.handshakeReceived) {
                conn.sendPacket(lazy { MsCodec.encode(message) })
                logger.trace { "$prefix - end: message sent" }
            } else {
                logger.debug { "$prefix - end: conn not found or has not received handshake yet" }
            }
            true
        } else {
            logger.debug { "$prefix - end: chain not found: ${blockchainRid.toShortHex()}" }
            false
        }
    }

    @Synchronized
    override fun disconnectSubChain(chainId: Long) {
        logger.debug("Disconnecting subnode chain")

        val chain = chainsWithOneSubConnection.get(chainId)
        if (chain != null) {
            chainsWithOneSubConnection.removeAndClose(chain.config.blockchainRid)
            logger.debug("Subnode chain disconnected")
        } else {
            logger.debug("Subnode chain is not connected")
        }
    }

    @Synchronized
    override fun onReceivedHandshake(blockchainRid: BlockchainRid) {
        logger.debug { "Received handshake for chain: ${blockchainRid.toShortHex()}" }
        chainsWithOneSubConnection.get(blockchainRid)?.handshakeReceived = true
    }

    @Synchronized
    override fun onSubConnected(
            descriptor: MasterConnectionDescriptor,
            connection: MasterConnection
    ): MsMessageHandler? {
        if (descriptor.blockchainRid == null) {
            queryConnections[descriptor.containerIID]?.close()

            queryConnections[descriptor.containerIID] = connection
            logger.debug { "Connected query runner for container: ${descriptor.containerIID}" }
            return MasterQueryHandler({ message -> connection.sendPacket(lazy { MsCodec.encode(message) }) },
                    masterSubQueryManager, dataSource, blockQueriesProvider)
        } else {
            val chain = chainsWithOneSubConnection.get(descriptor.blockchainRid)
            withLoggingContext(BLOCKCHAIN_RID_TAG to descriptor.blockchainRid.toHex()) {
                return when {
                    chain == null -> {
                        logger.warn("Sub chain not found")
                        connection.close()
                        null
                    }

                    chain.getConnection() != null -> {
                        logger.warn("Subnode already connected")
                        chain.closeConnection() // Close old connection here and store a new one
                        chain.setConnection(connection)
                        chain.config.messageHandler
                    }

                    else -> {
                        logger.info("Subnode connected")
                        chain.setConnection(connection)
                        chain.config.messageHandler
                    }
                }
            }
        }
    }

    @Synchronized
    override fun onSubDisconnected(
            descriptor: MasterConnectionDescriptor,
            connection: MasterConnection
    ) {
        if (descriptor.blockchainRid == null) {
            logger.debug { "Disconnected query runner for container: ${descriptor.containerIID}" }
            queryConnections.remove(descriptor.containerIID)?.close()
        } else {
            withLoggingContext(BLOCKCHAIN_RID_TAG to descriptor.blockchainRid.toHex()) {
                logger.debug("Subnode disconnected")

                val chain = chainsWithOneSubConnection.get(descriptor.blockchainRid)
                if (chain == null) {
                    connection.close()
                    logger.warn("Subnode chain not found")
                } else {
                    if (chain.getConnection() !== connection) { // Don't get it, is this expected?
                        connection.close()
                    }
                    chain.removeAndCloseConnection()
                }
            }
        }
    }

    override fun shutdown() {
        synchronized(this) {
            if (isShutDown) return
            isShutDown = true
            chainsWithOneSubConnection.removeAllAndClose()
            queryConnections.values.forEach { it.close() }
            queryConnections.clear()
        }
        // The shutdown is intentionally called outside the sync-block to avoid deadlock
        masterConnector.shutdown()
    }
}