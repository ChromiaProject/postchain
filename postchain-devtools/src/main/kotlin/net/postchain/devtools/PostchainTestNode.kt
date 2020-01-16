package net.postchain.devtools

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.api.rest.controller.Model
import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.NODE_ID_TODO
import net.postchain.devtools.utils.configuration.BlockchainSetup
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.Gtv
import net.postchain.gtv.gtvml.GtvMLParser

/**
 * This node is used in integration tests.
 *
 * @property nodeConfigProvider gives us the configuration of the node
 * @property preWipeDatabase is true if we want to start up clean (usually the case when we run tests)
 *
 */
class PostchainTestNode(
        nodeConfigProvider: NodeConfigurationProvider,
        preWipeDatabase: Boolean
) : PostchainNode(nodeConfigProvider) {

    private val storage: Storage
    val pubKey: String
    private var isInitialized = false

    init {
        val nodeConfig = nodeConfigProvider.getConfiguration()
        storage = StorageBuilder.buildStorage(nodeConfig, NODE_ID_TODO, preWipeDatabase)
        pubKey = nodeConfig.pubKey
    }

    companion object : KLogging() {
        const val DEFAULT_CHAIN_IID = 1L
    }

    private fun initDb(chainId: Long, blockchainRid: ByteArray) {
        // TODO: [et]: Is it necessary here after StorageBuilder.buildStorage() redesign?
        withWriteConnection(storage, chainId) { eContext ->
            with(DatabaseAccess.of(eContext)) {
                initialize(eContext.conn, expectedDbVersion = 1)
                checkBlockchainRID(eContext, blockchainRid)
            }
            true
        }

        isInitialized = true
    }

    fun addBlockchain(chainSetup: BlockchainSetup) {
        addBlockchain(chainSetup.chainId.toLong(), chainSetup.rid.hexStringToByteArray(), chainSetup.bcGtv)
    }

    fun addBlockchain(chainId: Long, blockchainRid: ByteArray, blockchainConfig: Gtv) {
        initDb(chainId, blockchainRid)
        addConfiguration(chainId, 0, blockchainConfig)
    }


    fun addConfiguration(chainId: Long, height: Long, blockchainConfig: Gtv) {
        check(isInitialized) { "PostchainNode is not initialized" }

        withWriteConnection(storage, chainId) { eContext ->
            logger.debug("Adding configuration for chain: $chainId, height: $height")
            BaseConfigurationDataStore.addConfigurationData(
                    eContext, height, encodeGtv(blockchainConfig))
            true
        }
    }

    fun startBlockchain() {
        startBlockchain(DEFAULT_CHAIN_IID)
    }

    override fun shutdown() {
        super.shutdown()
        storage.close()
    }

    fun getRestApiModel(): Model {
        val blockchainProcess = processManager.retrieveBlockchain(DEFAULT_CHAIN_IID)!!
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure).apiInfrastructure as BaseApiInfrastructure)
                .restApi?.retrieveModel(blockchainRID(blockchainProcess))!!
    }

    fun getRestApiHttpPort(): Int {
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure).apiInfrastructure as BaseApiInfrastructure)
                .restApi?.actualPort() ?: 0
    }

    fun getBlockchainInstance(chainId: Long = DEFAULT_CHAIN_IID): BlockchainProcess {
        return processManager.retrieveBlockchain(chainId) as BlockchainProcess
    }

    fun retrieveBlockchain(chainId: Long = DEFAULT_CHAIN_IID): BlockchainProcess? {
        return processManager.retrieveBlockchain(chainId)
    }

    fun transactionQueue(chainId: Long = DEFAULT_CHAIN_IID): TransactionQueue {
        return getBlockchainInstance(chainId).getEngine().getTransactionQueue()
    }

    fun blockQueries(chainId: Long = DEFAULT_CHAIN_IID): BlockQueries {
        return getBlockchainInstance(chainId).getEngine().getBlockQueries()
    }

    fun blockBuildingStrategy(chainId: Long = DEFAULT_CHAIN_IID): BlockBuildingStrategy {
        return getBlockchainInstance(chainId).getEngine().getBlockBuildingStrategy()
    }

    fun networkTopology(chainId: Long = DEFAULT_CHAIN_IID): Map<String, String> {
        // TODO: [et]: Fix type casting
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure)
                .synchronizationInfrastructure as EBFTSynchronizationInfrastructure)
                .connectionManager.getPeersTopology(chainId)
                .mapKeys { pubKeyToConnection ->
                    pubKeyToConnection.key.toString()
                }
    }

    private fun blockchainRID(process: BlockchainProcess): String {
        return (process.getEngine().getConfiguration() as BaseBlockchainConfiguration) // TODO: [et]: Resolve type cast
                .blockchainRID.toHex()
    }
}