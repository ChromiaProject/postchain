// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.PostchainContext
import net.postchain.StorageBuilder
import net.postchain.base.config.BlockchainConfig
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.common.reflection.constructorOf
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName

open class BaseBlockchainInfrastructure(
        val defaultSynchronizationInfrastructure: SynchronizationInfrastructure,
        val apiInfrastructure: ApiInfrastructure,
        private val postchainContext: PostchainContext
) : BlockchainInfrastructure {

    val cryptoSystem = SECP256K1CryptoSystem()
    val blockSigMaker: SigMaker
    val subjectID: ByteArray

    val syncInfraCache = mutableMapOf<String, SynchronizationInfrastructure>()
    val syncInfraExtCache = mutableMapOf<String, SynchronizationInfrastructureExtension>()

    init {
        val privKey = postchainContext.nodeConfig.privKeyByteArray
        val pubKey = secp256k1_derivePubKey(privKey)
        blockSigMaker = cryptoSystem.buildSigMaker(pubKey, privKey)
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
    ): BlockchainConfiguration {

        val chainConfig = BlockchainConfig.fromRaw(rawConfigurationData, blockSigMaker, eContext, nodeId, chainId, subjectID)

        val factory = newInstanceOf<BlockchainConfigurationFactory>(chainConfig.blockchainConfigFactory)
        val config = factory.makeBlockchainConfiguration(chainConfig)
        config.initializeDB(eContext)

        return config
    }

    override fun makeBlockchainEngine(
            processName: BlockchainProcessName,
            configuration: BlockchainConfiguration,
            afterCommitHandler: AfterCommitHandler
    ): BaseBlockchainEngine {

        // We create a new storage instance to open new db connections for each engine
        val storage = StorageBuilder.buildStorage(postchainContext.nodeConfig.appConfig)

        val transactionQueue = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration) // TODO: Olle: Is this conversion harmless?
                        .configData.blockBuildingStrategyConfig.queueCapacity)

        return BaseBlockchainEngine(processName, configuration, storage, configuration.chainID, transactionQueue)
                .apply {
                    setAfterCommitHandler(afterCommitHandler)
                    initialize()
                }
    }

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            shouldProcessNewMessages: (Long) -> Boolean
    ): BlockchainProcess {
        val configuration = engine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        val process = synchronizationInfrastructure.makeBlockchainProcess(processName, engine, shouldProcessNewMessages)
        connectProcess(configuration, process)
        return process
    }

    override fun exitBlockchainProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        synchronizationInfrastructure.exitBlockchainProcess(process)
        disconnectProcess(configuration, process)
    }

    override fun restartBlockchainProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(configuration.syncInfrastructureName)
        synchronizationInfrastructure.restartBlockchainProcess(process)
        disconnectProcess(configuration, process)
    }

    private fun connectProcess(configuration: BlockchainConfiguration , process: BlockchainProcess) {
        configuration.syncInfrastructureExtensionNames.forEach {
            getSynchronizationInfrastructureExtension(it).connectProcess(process)
        }
        apiInfrastructure.connectProcess(process)
    }

    private fun disconnectProcess(configuration: BlockchainConfiguration, process: BlockchainProcess) {
        configuration.syncInfrastructureExtensionNames.forEach {
            getSynchronizationInfrastructureExtension(it).disconnectProcess(process)
        }
        apiInfrastructure.disconnectProcess(process)
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
