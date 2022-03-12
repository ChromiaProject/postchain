// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.PostchainContext
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockchainConfigurationData.Companion.KEY_CONFIGURATIONFACTORY
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatListener


open class BaseBlockchainInfrastructure(
        val defaultSynchronizationInfrastructure: SynchronizationInfrastructure,
        val apiInfrastructure: ApiInfrastructure,
        private val postchainContext: PostchainContext
) : BlockchainInfrastructure, SynchronizationInfrastructure by defaultSynchronizationInfrastructure {

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
        for (infra in syncInfraCache.values)
            infra.shutdown()
        for (ext in syncInfraExtCache.values)
            ext.shutdown()
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
            configurationComponentMap: MutableMap<String, Any>
    ): BlockchainConfiguration {

        val confData = BaseBlockchainConfigurationData.build(
            rawConfigurationData, eContext, nodeId, chainId, subjectID, blockSigMaker, configurationComponentMap)

        val bcfClass = Class.forName(confData.data[KEY_CONFIGURATIONFACTORY]!!.asString())
        val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

        val config = factory.makeBlockchainConfiguration(confData)
        config.initializeDB(eContext)

        return config
    }

    override fun makeBlockchainEngine(
            processName: BlockchainProcessName,
            configuration: BlockchainConfiguration,
            afterCommitHandler: AfterCommitHandler
    ): BaseBlockchainEngine {

        val storage = StorageBuilder.buildStorage(postchainContext.nodeConfig.appConfig, NODE_ID_TODO)

        val transactionQueue = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration) // TODO: Olle: Is this conversion harmless?
                        .configData.getQueueCapacity())

        return BaseBlockchainEngine(processName, configuration, storage, configuration.chainID, transactionQueue)
                .apply {
                    setAfterCommitHandler(afterCommitHandler)
                    initialize()
                }
    }

    private fun getSynchronizationInfrastructure(dynClassName: DynamicClassName?): SynchronizationInfrastructure {
        if (dynClassName == null) return defaultSynchronizationInfrastructure
        val name = dynClassName.className
        val className = if (name == "ebft") "net.postchain.ebft.EBFTSynchronizationInfrastructure" else name
        return syncInfraCache.getOrPut(className) { getInstanceByClassName(className) }
    }

    private fun getSynchronizationInfrastructureExtension(dynClassName: DynamicClassName): SynchronizationInfrastructureExtension {
        return syncInfraExtCache.getOrPut(dynClassName.className) { getInstanceByClassName(dynClassName.className) }
    }

    /**
     * Will dynamically create an instance from the given class name (with the constructor params nodeConfigParam
     * and nodeDiagnosticCtx).
     *
     * @param className is the full name of the class to create an instance from
     * @return the instance as a [Shutdownable]
     */
    private inline fun <reified T : Shutdownable> getInstanceByClassName(className: String): T {
        val iClass = Class.forName(className)
        val ctor = iClass.getConstructor(PostchainContext::class.java)
        val instance = ctor.newInstance(postchainContext)
        if (instance is T)
            return instance
        else
            throw UserMistake(
                    "Class ${className} does not support required interface"
            )
    }

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            heartbeatListener: HeartbeatListener?
    ): BlockchainProcess {
        val conf = engine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastructure(conf.syncInfrastructureName)
        val process = synchronizationInfrastructure.makeBlockchainProcess(processName, engine, heartbeatListener)
        if (conf is BaseBlockchainConfiguration) {
            for (extName in conf.syncInfrastructureExtensionNames) {
                getSynchronizationInfrastructureExtension(extName).connectProcess(process)
            }
        }
        apiInfrastructure.connectProcess(process)
        return process
    }
}
