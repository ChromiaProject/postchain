package net.postchain.network.mastersub.master

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.debug.BlockchainProcessName
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
        private val containerNodeConfig: ContainerNodeConfig,
        private val blockQueriesProvider: BlockQueriesProvider
) : MasterConnectionManager, MasterConnectorEvents {

    companion object : KLogging()

    override val masterSubQueryManager = MasterSubQueryManager { blockchainRid, message ->
        if (blockchainRid == null) throw ProgrammerMistake("Missing destination chain")
        sendPacketToSub(blockchainRid, message)
    }

    var isShutDown = false

    // Here we don't bother with factory
    private val masterConnector = NettyMasterConnector(this).apply {
        init(containerNodeConfig.masterPort)
    }

    private val chainsWithOneSubConnection = ChainsWithOneConnection<
            MsMessageHandler,
            MasterConnection,
            ChainWithOneSubConnection>()

    override lateinit var dataSource: ManagedNodeDataSource
    private val queryConnections = mutableMapOf<Int, MasterConnection>()

    @Synchronized
    override fun initSubChainConnection(processName: BlockchainProcessName, subChainConfig: SubChainConfig) {
        val logMsg = subChainConfig.log()
        logger.debug { "$processName: connectSubChain() - Initializing subnode chain connection: $logMsg" }

        if (isShutDown) {
            logger.warn(
                    "$processName: connectSubChain() - Already shut down: connecting subnode chains is " +
                            "not possible. $logMsg"
            )
        } else {
            if (chainsWithOneSubConnection.hasChain(subChainConfig.blockchainRid)) {
                // TODO: Olle: This needs some explanation. Why do we "disconnect" and
                //  then "connect" on an existing connection? Do we expect it to be stale?
                logger.info(
                        "$processName: connectSubChain() - This chain is already connected, disconnecting " +
                                "old sub-node connection first. $logMsg"
                )
                disconnectSubChain(processName, subChainConfig.chainId)
            }
            chainsWithOneSubConnection.add(ChainWithOneSubConnection(subChainConfig))
            logger.debug { "$processName: connectSubChain() - Subnode chain connection initialized: $logMsg" }
        }
    }

    @Synchronized
    override fun sendPacketToSub(blockchainRid: BlockchainRid, message: MsMessage): Boolean {
        logger.debug { "sendPacketToSub() - begin, type: ${message.type}" }
        val chain = chainsWithOneSubConnection.get(blockchainRid)
        return if (chain != null) {
            val conn = chain.getConnection()
            if (conn != null) {
                conn.sendPacket { MsCodec.encode(message) }
                logger.trace { "sendPacketToSub() - end: message sent" }
            } else {
                logger.debug { "sendPacketToSub() - end: conn not found" }
            }
            true
        } else {
            logger.debug { "sendPacketToSub() - end: chain not found: ${blockchainRid.toShortHex()}" }
            false
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
        if (descriptor.blockchainRid == null) {
            queryConnections[descriptor.containerIID]?.close()

            queryConnections[descriptor.containerIID] = connection
            logger.debug { "Connected query runner for container: ${descriptor.containerIID}" }
            return MasterQueryHandler({ message -> connection.sendPacket { MsCodec.encode(message) } },
                    masterSubQueryManager, dataSource, blockQueriesProvider)
        } else {
            val processName = buildProcessName(descriptor.blockchainRid)
            val chain = chainsWithOneSubConnection.get(descriptor.blockchainRid)
            return when {
                chain == null -> {
                    logger.warn("$processName: Sub chain not found by blockchainRid = ${descriptor.blockchainRid}")
                    connection.close()
                    null
                }

                chain.getConnection() != null -> {
                    logger.warn { "$processName: Subnode already connected: blockchainRid = ${descriptor.blockchainRid.toShortHex()}" }
                    chain.closeConnection() // Close old connection here and store a new one
                    chain.setConnection(connection)
                    chain.config.messageHandler
                }

                else -> {
                    logger.info { "$processName: Subnode connected: blockchainRid: ${descriptor.blockchainRid.toShortHex()}" }
                    chain.setConnection(connection)
                    chain.config.messageHandler
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
            val processName = buildProcessName(descriptor.blockchainRid)
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

    private fun buildProcessName(blockchainRid: BlockchainRid): String = BlockchainProcessName(
            appConfig.pubKey, blockchainRid
    ).toString()
}