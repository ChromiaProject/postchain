// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.PostchainContext
import net.postchain.StorageBuilder
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.common.reflection.constructorOf
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.*
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.core.block.*
import net.postchain.core.*
import net.postchain.core.block.BlockchainProcessName

open class BaseBlockchainInfrastructure(
        val defaultSynchronizationInfrastructure: SynchronizationInfrastructure,
        val apiInfrastructure: ApiInfrastructure,
        private val postchainContext: PostchainContext
) : BlockchainInfrastructure {

    val cryptoSystem = Secp256K1CryptoSystem()
    val blockSigMaker: SigMaker
    val subjectID: ByteArray

    val syncInfraCache = mutableMapOf<String, SynchronizationInfrastructure>()
    val syncInfraExtCache = mutableMapOf<String, SynchronizationInfrastructureExtension>()

    init {
        val privKey = postchainContext.appConfig.privKeyByteArray
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

        val blockConfData = BlockchainConfigurationData.fromRaw(
                rawConfigurationData, eContext, nodeId, chainId, subjectID, blockSigMaker)

        val factory = newInstanceOf<BlockchainConfigurationFactory>(blockConfData.configurationFactory)
        val config = factory.makeBlockchainConfiguration(blockConfData)
        config.initializeDB(eContext)

        return config
    }

    override fun makeBlockchainEngine(
        processName: BlockchainProcessName,
        configuration: BlockchainConfiguration,
        afterCommitHandler: AfterCommitHandler
    ): BaseBlockchainEngine {

        // We create a new storage instance to open new db connections for each engine
        val storage = StorageBuilder.buildStorage(postchainContext.appConfig)

        val transactionQueue = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration) // TODO: Olle: Is this conversion harmless?
                        .configData.txQueueSize.toInt())

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
