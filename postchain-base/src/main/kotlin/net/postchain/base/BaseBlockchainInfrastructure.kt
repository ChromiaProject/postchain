// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.PostchainContext
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockchainConfigurationData.Companion.KEY_CONFIGURATIONFACTORY
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory

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
     * @param initialBlockchainRID is null or a blokchain RID
     * @return the newly created [BlockchainConfiguration]
     */
    override fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long
    ): BlockchainConfiguration {

        val gtvData = GtvFactory.decodeGtv(rawConfigurationData)
        val brid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!

        val context = BaseBlockchainContext(brid, nodeId, chainId, subjectID)
        val confData = BaseBlockchainConfigurationData(gtvData as GtvDictionary, context, blockSigMaker)

        val bcfClass = Class.forName(confData.data[KEY_CONFIGURATIONFACTORY]!!.asString())
        val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

        val config = factory.makeBlockchainConfiguration(confData)
        config.initializeDB(eContext)

        return config
    }

    override fun makeBlockchainEngine(
            processName: BlockchainProcessName,
            configuration: BlockchainConfiguration,
            restartHandler: RestartHandler
    ): BaseBlockchainEngine {

        // We create a new storage instance to open new db connections for each engine
        val storage = StorageBuilder.buildStorage(postchainContext.nodeConfig.appConfig)

        val transactionQueue = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration) // TODO: Olle: Is this conversion harmless?
                        .configData.getQueueCapacity())

        return BaseBlockchainEngine(processName, configuration, storage, configuration.chainID, transactionQueue)
                .apply {
                    setRestartHandler(restartHandler)
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
        return syncInfraCache.getOrPut(className) { getInstanceByClassName(className) }
    }

    private fun getSynchronizationInfrastructureExtension(dynClassName: DynamicClassName): SynchronizationInfrastructureExtension {
        return syncInfraExtCache.getOrPut(dynClassName.className) { getInstanceByClassName(dynClassName.className) }
    }

    /**
     * Will dynamically create an instance from the given class name (with the constructor param [PostchainContext]).
     *
     * @param className is the full name of the class to create an instance from
     * @return the instance as a subclass of [Shutdownable]
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Shutdownable> getInstanceByClassName(className: String): T {
        val iClass = Class.forName(className)
        val ctor = iClass.getConstructor(PostchainContext::class.java)
        return ctor.newInstance(postchainContext) as T
    }
}
