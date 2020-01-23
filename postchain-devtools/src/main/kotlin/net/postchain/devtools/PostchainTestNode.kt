// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.api.rest.controller.Model
import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.gtv.Gtv

class PostchainTestNode(nodeConfigProvider: NodeConfigurationProvider, preWipeDatabase: Boolean = false) : PostchainNode(nodeConfigProvider) {

    private val testStorage: Storage
    val pubKey: String
    private var isInitialized = false
    private val blockchainRidMap = mutableMapOf<Long, BlockchainRid>() // Used to keep track of the BC RIDs of the chains

    init {
        val nodeConfig = nodeConfigProvider.getConfiguration()
        testStorage = StorageBuilder.buildStorage(nodeConfig.appConfig, NODE_ID_TODO, preWipeDatabase)
        pubKey = nodeConfig.pubKey
    }

    companion object : KLogging() {
        const val SYSTEM_CHAIN_IID = 0L
        const val DEFAULT_CHAIN_IID = 1L
    }

    private fun initDb(chainId: Long) {
        // TODO: [et]: Is it necessary here after StorageBuilder.buildStorage() redesign?
        withWriteConnection(testStorage, chainId) { eContext ->
            with(DatabaseAccess.of(eContext)) {
                initialize(eContext.conn, expectedDbVersion = 1)
            }
            true
        }

        isInitialized = true
    }

    fun addBlockchain(chainId: Long, blockchainConfig: Gtv): BlockchainRid {
        initDb(chainId)
        return addConfiguration(chainId, 0, blockchainConfig)
    }

    fun addConfiguration(chainId: Long, height: Long, blockchainConfig: Gtv): BlockchainRid {
        check(isInitialized) { "PostchainNode is not initialized" }

        return withReadWriteConnection(testStorage, chainId) { eContext: EContext ->
            BaseConfigurationDataStore.addConfigurationData(
                    eContext, height, blockchainConfig)
        }
    }

    fun startBlockchain(): BlockchainRid? {
        return startBlockchain(DEFAULT_CHAIN_IID)
    }

    override fun shutdown() {
        super.shutdown()
        testStorage.close()
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

    fun mapBlockchainRID(chainId: Long, bcRID: BlockchainRid) {
        blockchainRidMap[chainId] = bcRID
    }

    /**
     * Yeah I know this is a strange way of retrieving the BC RID, but plz change if you think of something better.
     * (It's only for test, so I didn't ptu much thought into it. )
     */
    fun getBlockchainRid(chainId: Long): BlockchainRid? = blockchainRidMap[chainId]

    private fun blockchainRID(process: BlockchainProcess): String {
        return (process.getEngine().getConfiguration() as BaseBlockchainConfiguration) // TODO: [et]: Resolve type cast
                .blockchainRID.toHex()
    }
}