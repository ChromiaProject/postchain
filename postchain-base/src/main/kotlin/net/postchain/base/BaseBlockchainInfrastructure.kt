// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.StorageBuilder
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.reflection.constructorOf
import net.postchain.core.*
import net.postchain.core.block.*
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.debug.BlockchainProcessName

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
        val privKey = postchainContext.appConfig.privKeyByteArray
        val pubKey = secp256k1_derivePubKey(privKey)
        blockSigMaker = postchainContext.cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        subjectID = pubKey
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
     * @param configurationComponentMap is the map of components (of any type) we specifically set for this config.
     * @return the newly created [BlockchainConfiguration]
     */
    override fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long,
            bcConfigurationFactory: BlockchainConfigurationFactorySupplier,
    ): BlockchainConfiguration {

        val blockConfData = BlockchainConfigurationData.fromRaw(
                rawConfigurationData, eContext, nodeId, chainId, subjectID, blockSigMaker)

        val factory = bcConfigurationFactory.supply(blockConfData.configurationFactory)

        return factory.makeBlockchainConfiguration(blockConfData, eContext, postchainContext.cryptoSystem)
    }

    override fun makeBlockchainEngine(
            processName: BlockchainProcessName,
            configuration: BlockchainConfiguration,
            afterCommitHandler: AfterCommitHandler
    ): BaseBlockchainEngine {

        // We create a new storage instance to open new db connections for each engine
        val storage = StorageBuilder.buildStorage(postchainContext.appConfig)

        val transactionQueue = BaseTransactionQueue(configuration.transactionQueueSize)

        return BaseBlockchainEngine(processName, configuration, storage, configuration.chainID, transactionQueue)
                .apply {
                    setAfterCommitHandler(afterCommitHandler)
                    initialize()
                }
    }

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            awaitPermissionToProcessMessages: (timestamp: Long, exitCondition: () -> Boolean) -> Boolean
    ): BlockchainProcess {
        val configuration = engine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        val process = synchronizationInfrastructure.makeBlockchainProcess(processName, engine, awaitPermissionToProcessMessages)
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
                throw ProgrammerMistake("Error when connecting sync-infra extension: ${it.className}", e)
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
