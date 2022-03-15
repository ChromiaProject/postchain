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
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory

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
    @Suppress("UNCHECKED_CAST")
    private fun <T : Shutdownable> getInstanceByClassName(className: String): T {
        val iClass = Class.forName(className)
        val ctor = iClass.getConstructor(PostchainContext::class.java)
        return ctor.newInstance(postchainContext) as T
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
