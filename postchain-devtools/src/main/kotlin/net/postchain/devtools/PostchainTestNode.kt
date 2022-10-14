// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainNode
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.core.*
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.devtools.NameHelper.peerName
import net.postchain.devtools.utils.configuration.BlockchainSetup
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.gtv.Gtv
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.metrics.BLOCKCHAIN_RID_TAG
import net.postchain.metrics.CHAIN_IID_TAG
import net.postchain.metrics.NODE_PUBKEY_TAG
import kotlin.properties.Delegates

/**
 * This node is used in integration tests.
 *
 * @property nodeConfigProvider gives us the configuration of the node
 * @property preWipeDatabase is true if we want to start up clean (usually the case when we run tests)
 *
 */
class PostchainTestNode(
        appConfig: AppConfig,
        wipeDb: Boolean,
        debug: Boolean = false
) : PostchainNode(appConfig, wipeDb, debug) {

    val pubKey: String
    private var isInitialized by Delegates.notNull<Boolean>()
    private val blockchainRidMap = mutableMapOf<Long, BlockchainRid>() // Used to keep track of the BC RIDs of the chains

    init {
        pubKey = appConfig.pubKey
        isInitialized = true

        // We don't have specific test classes for Proc Man
        when (processManager) {
            is BaseBlockchainProcessManager -> {
                processManager.insideATest = true
            }
            is ManagedBlockchainProcessManager -> {
                processManager.insideATest = true
            }
        }
    }

    companion object : KLogging() {
        const val SYSTEM_CHAIN_IID = 0L
        const val DEFAULT_CHAIN_IID = 1L
    }

    override fun isThisATest() = true

    fun addBlockchain(chainSetup: BlockchainSetup) {
        addBlockchain(chainSetup.chainId.toLong(), chainSetup.bcGtv)
    }

    fun addBlockchain(chainId: Long, blockchainConfig: Gtv): BlockchainRid {
        check(isInitialized) { "PostchainNode is not initialized" }

        return withReadWriteConnection(postchainContext.storage, chainId) { eContext: EContext ->
            val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, postchainContext.cryptoSystem)
            withLoggingContext(
                NODE_PUBKEY_TAG to appConfig.pubKey,
                CHAIN_IID_TAG to chainId.toString(),
                BLOCKCHAIN_RID_TAG to brid.toHex()
            ) {
                logger.info("Adding blockchain: chainId: $chainId, blockchainRid: ${brid.toHex()}") // Needs to be info, since users often don't know the BC RID and take it from the logs
                DatabaseAccess.of(eContext).initializeBlockchain(eContext, brid)
                BaseConfigurationDataStore.addConfigurationData(eContext, 0, blockchainConfig)
            }
            brid
        }
    }

    fun addConfiguration(chainId: Long, height: Long, blockchainConfig: Gtv): BlockchainRid {
        check(isInitialized) { "PostchainNode is not initialized" }

        return withReadWriteConnection(postchainContext.storage, chainId) { eContext: EContext ->
            val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, postchainContext.cryptoSystem)
            withLoggingContext(
                NODE_PUBKEY_TAG to appConfig.pubKey,
                CHAIN_IID_TAG to chainId.toString(),
                BLOCKCHAIN_RID_TAG to brid.toHex()
            ) {
                logger.info("Adding configuration for chain: $chainId, height: $height") // Needs to be info, since users often don't know the BC RID and take it from the logs
                BaseConfigurationDataStore.addConfigurationData(eContext, height, blockchainConfig)
            }
            brid
        }
    }

    fun setMustSyncUntil(chainId: Long, brid: BlockchainRid, height: Long): Boolean {
        check(isInitialized) { "PostchainNode is not initialized" }

        return withReadWriteConnection(postchainContext.storage, chainId) { eContext: EContext ->
            withLoggingContext(
                NODE_PUBKEY_TAG to appConfig.pubKey,
                CHAIN_IID_TAG to chainId.toString(),
                BLOCKCHAIN_RID_TAG to brid.toHex()
            ) {
                logger.debug("Set must_sync_until for chain: $brid, height: $height")
                BaseConfigurationDataStore.setMustSyncUntil(eContext, brid, height)
            }
        }
    }

    fun startBlockchain() {
        withLoggingContext(
            NODE_PUBKEY_TAG to appConfig.pubKey,
            CHAIN_IID_TAG to DEFAULT_CHAIN_IID.toString()
        ) {
            try {
                startBlockchain(DEFAULT_CHAIN_IID)
            } catch (e: NotFound) {
                logger.error(e.message)
            } catch (e: UserMistake) {
                logger.error(e.message)
            } catch (e: Exception) {
                logger.error(e) { e.message }
            }
        }
    }

    override fun shutdown() {
        withLoggingContext(NODE_PUBKEY_TAG to appConfig.pubKey) {
            logger.debug("shutdown node ${peerName(pubKey)}")
            super.shutdown()
            logger.debug("shutdown node ${peerName(pubKey)} done")
        }
    }

    fun getRestApiModel(): Model {
        val blockchainProcess = processManager.retrieveBlockchain(DEFAULT_CHAIN_IID)!!
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure).apiInfrastructure as BaseApiInfrastructure)
                .restApi?.retrieveModel(blockchainRID(blockchainProcess)) as Model
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
        return getBlockchainInstance(chainId).blockchainEngine.getTransactionQueue()
    }

    fun blockQueries(chainId: Long = DEFAULT_CHAIN_IID): BlockQueries {
        return getBlockchainInstance(chainId).blockchainEngine.getBlockQueries()
    }

    fun blockBuildingStrategy(chainId: Long = DEFAULT_CHAIN_IID): BlockBuildingStrategy {
        return getBlockchainInstance(chainId).blockchainEngine.getBlockBuildingStrategy()
    }

    fun networkTopology(chainId: Long = DEFAULT_CHAIN_IID): Map<String, String> {
        // TODO: [et]: Fix type casting
        return ((blockchainInfrastructure as BaseBlockchainInfrastructure)
                .defaultSynchronizationInfrastructure as EBFTSynchronizationInfrastructure)
                .connectionManager.getNodesTopology(chainId)
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
        return process.blockchainEngine.getConfiguration().blockchainRid.toHex()
    }
}