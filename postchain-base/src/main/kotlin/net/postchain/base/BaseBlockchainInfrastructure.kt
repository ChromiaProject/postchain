// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.constructorOf
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BeforeCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactorySupplier
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.BlockchainState
import net.postchain.core.DynamicClassName
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.metrics.BaseBlockchainEngineMetrics
import kotlin.time.Duration.Companion.minutes

open class BaseBlockchainInfrastructure(
        val defaultSynchronizationInfrastructure: SynchronizationInfrastructure,
        val apiInfrastructure: ApiInfrastructure,
        private val postchainContext: PostchainContext
) : BlockchainInfrastructure {

    val blockSigMaker: SigMaker
    val subjectID: ByteArray

    val syncInfraCache = mutableMapOf<String, SynchronizationInfrastructure>()
    val syncInfraExtCache = mutableMapOf<String, SynchronizationInfrastructureExtension>()

    companion object : KLogging()

    init {
        val privKey = PrivKey(postchainContext.appConfig.privKeyByteArray)
        val pubKey = postchainContext.cryptoSystem.derivePubKey(privKey)
        if (!postchainContext.appConfig.pubKeyByteArray.contentEquals(pubKey.data)) {
            throw UserMistake("Derived pubkey from private key $pubKey does not match configured pubkey ${postchainContext.appConfig.pubKey}")
        }
        blockSigMaker = postchainContext.cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        subjectID = pubKey.data
        syncInfraCache[defaultSynchronizationInfrastructure.javaClass.name] = defaultSynchronizationInfrastructure
    }

    override fun shutdown() {
        syncInfraCache.values.forEach { it.shutdown() }
        syncInfraExtCache.values.forEach { it.shutdown() }
        apiInfrastructure.shutdown()
    }

    /**
     * Builds a [BlockchainConfiguration] instance from the given components
     *
     * @param rawConfigurationData is the byte array with the configuration.
     * @param eContext is the DB context
     * @param nodeId
     * @param chainId
     * @return the newly created [BlockchainConfiguration]
     */
    override fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long,
            bcConfigurationFactory: BlockchainConfigurationFactorySupplier,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ): BlockchainConfiguration {
        val blockConfData = BlockchainConfigurationData.fromRaw(rawConfigurationData)

        val blockchainRid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!
        val partialContext = BaseBlockchainContext(chainId, blockchainRid, nodeId, subjectID)

        val factory = bcConfigurationFactory.supply(blockConfData.configurationFactory)

        return factory.makeBlockchainConfiguration(
                blockConfData, partialContext, blockSigMaker, eContext, postchainContext.cryptoSystem, blockchainConfigurationOptions)
    }

    override fun makeBlockchainEngine(
            configuration: BlockchainConfiguration,
            beforeCommitHandler: BeforeCommitHandler,
            afterCommitHandler: AfterCommitHandler,
            blockBuilderStorage: Storage,
            sharedStorage: Storage,
            initialEContext: EContext,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier
    ): BaseBlockchainEngine {
        val blockQueries: BlockQueries = configuration.makeBlockQueries(sharedStorage)
        val transactionQueue = BaseTransactionQueue(
                configuration.transactionQueueSize,
                recheckThreadInterval = 1.minutes,
                recheckTxInterval = configuration.transactionQueueRecheckInterval,
                if (configuration.hasQuery(PRIORITIZE_QUERY_NAME))
                    BaseTransactionPrioritizer { name: String, args: Gtv ->
                        blockQueries.query(name, args).toCompletableFuture().get()
                    }
                else
                    null
        )
        val metrics = BaseBlockchainEngineMetrics(configuration.chainID, configuration.blockchainRid, transactionQueue)
        val strategy: BlockBuildingStrategy = configuration.getBlockBuildingStrategy(blockQueries, transactionQueue)

        return BaseBlockchainEngine(configuration, blockBuilderStorage, sharedStorage, configuration.chainID,
                initialEContext, blockchainConfigurationProvider, restartNotifier,
                postchainContext.nodeDiagnosticContext, beforeCommitHandler, afterCommitHandler, true,
                blockQueries, transactionQueue, metrics, strategy)
    }

    override fun makeBlockchainProcess(
            engine: BlockchainEngine,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ): BlockchainProcess {
        val configuration = engine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        val process = synchronizationInfrastructure.makeBlockchainProcess(engine, blockchainConfigurationProvider, restartNotifier, blockchainState)
        try {
            connectProcess(configuration, process)
        } catch (e: Exception) {
            // Clean up any resources that may have been created when instantiating blockchain process
            process.shutdown()
            throw e
        }
        // Start the process once we have connected all the infra successfully
        return process.apply { start() }
    }

    override fun exitBlockchainProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        synchronizationInfrastructure.exitBlockchainProcess(process)
        disconnectProcess(configuration, process, false)
    }

    override fun restartBlockchainProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        synchronizationInfrastructure.restartBlockchainProcess(process)
        disconnectProcess(configuration, process, true)
    }

    private fun connectProcess(configuration: BlockchainConfiguration, process: BlockchainProcess) {
        configuration.syncInfrastructureExtensionNames.forEach {
            try {
                getSynchronizationInfrastructureExtension(it).connectProcess(process)
            } catch (e: Exception) {
                throw ProgrammerMistake("Error when connecting sync-infra extension ${it.className}: ${e.message}", e)
            }
        }
        apiInfrastructure.connectProcess(process)
    }

    private fun disconnectProcess(
            configuration: BlockchainConfiguration,
            process: BlockchainProcess,
            isRestarting: Boolean
    ) {
        configuration.syncInfrastructureExtensionNames.forEach {
            try {
                getSynchronizationInfrastructureExtension(it).disconnectProcess(process)
            } catch (e: Exception) {
                logger.error("Error when disconnecting sync-infra extension: ${it.className}", e)
            }
        }
        if (isRestarting) {
            apiInfrastructure.restartProcess(process)
        } else {
            apiInfrastructure.disconnectProcess(process)
        }
    }

    private fun getSynchronizationInfrastructure(dynClassName: DynamicClassName?): SynchronizationInfrastructure {
        if (dynClassName == null) return defaultSynchronizationInfrastructure
        val className = dynClassName.className.let {
            if (it == "ebft") "net.postchain.ebft.EBFTSynchronizationInfrastructure" else it
        }
        return syncInfraCache.getOrPut(className) { constructorOf<SynchronizationInfrastructure>(className, PostchainContext::class.java).newInstance(postchainContext) }
    }

    private fun getSynchronizationInfrastructureExtension(dynClassName: DynamicClassName): SynchronizationInfrastructureExtension {
        return syncInfraExtCache.getOrPut(dynClassName.className) { constructorOf<SynchronizationInfrastructureExtension>(dynClassName.className, PostchainContext::class.java).newInstance(postchainContext) }
    }
}
