// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockchainConfigurationData.Companion.KEY_CONFIGURATIONFACTORY
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory

open class BaseBlockchainInfrastructure(
    private val nodeConfigProvider: NodeConfigurationProvider,
    val defaultSynchronizationInfrastructure: SynchronizationInfrastructure,
    val apiInfrastructure: ApiInfrastructure,
    val nodeDiagnosticContext: NodeDiagnosticContext
) : BlockchainInfrastructure, SynchronizationInfrastructure by defaultSynchronizationInfrastructure {

    val cryptoSystem = SECP256K1CryptoSystem()
    val blockSigMaker: SigMaker
    val subjectID: ByteArray

    val syncInfraCache = mutableMapOf<String, SynchronizationInfrastructure>()
    val syncInfraExtCache = mutableMapOf<String, SynchronizationInfrastructureExtension>()

    init {
        val privKey = nodeConfigProvider.getConfiguration().privKeyByteArray
        val pubKey = secp256k1_derivePubKey(privKey)
        blockSigMaker = cryptoSystem.buildSigMaker(pubKey, privKey)
        subjectID = pubKey
        syncInfraCache[defaultSynchronizationInfrastructure.javaClass.name] = defaultSynchronizationInfrastructure
    }

    override fun init() {}

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

        val storage = StorageBuilder.buildStorage(
                nodeConfigProvider.getConfiguration().appConfig, NODE_ID_TODO)

        val transactionQueue = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration) // TODO: Olle: Is this conversion harmless?
                        .configData.getQueueCapacity())

        return BaseBlockchainEngine(processName, configuration, storage, configuration.chainID, transactionQueue)
                .apply {
                    setRestartHandler(restartHandler)
                    initialize()
                }
    }

    fun getSynchronizationInfrastucture(dynClassName: DynamicClassName?): SynchronizationInfrastructure {
        if (dynClassName == null) return defaultSynchronizationInfrastructure
        val name = dynClassName.className
        val full_name = if (name == "ebft") "net.postchain.ebft.EBFTSynchronizationInfrastructure" else name
        if (full_name in syncInfraCache) return syncInfraCache[full_name]!!
        val infra = getInstanceByClassName(name) as SynchronizationInfrastructure
        syncInfraCache[full_name] = infra
        return infra
    }

    fun getSynchronizationInfrastuctureExtension(dynClassName: DynamicClassName): SynchronizationInfrastructureExtension {
        val name = dynClassName.className
        if (name in syncInfraCache) return syncInfraExtCache[name]!!
        val infra = getInstanceByClassName(name) as SynchronizationInfrastructureExtension
        syncInfraExtCache[name] = infra
        return infra
    }

    /**
     * Will dynamically create an instance from the given class name (with the constructor params nodeConfigParam
     * and nodeDiagnosticCtx).
     *
     * @param className is the full name of the class to create an instance from
     * @return the instance as a [Shutdownable]
     */
    private fun getInstanceByClassName(className: String): Shutdownable {
        val iClass = Class.forName(className)
        val ctor = iClass.getConstructor(
            NodeConfigurationProvider::class.java,
            NodeDiagnosticContext::class.java
        )
        return ctor.newInstance(nodeConfigProvider, nodeDiagnosticContext) as Shutdownable
    }

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            heartbeatListener: HeartbeatListener?,
            historicBlockchainContext: HistoricBlockchainContext?
    ): BlockchainProcess {
        val conf = engine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastucture(conf.syncInfrastructureName)
        val process = synchronizationInfrastructure.makeBlockchainProcess(processName, engine, heartbeatListener, historicBlockchainContext)
        if (conf is BaseBlockchainConfiguration) {
            for (extName in conf.syncInfrastructureExtensionNames) {
                getSynchronizationInfrastuctureExtension(extName).connectProcess(process)
            }
        }
        apiInfrastructure.connectProcess(process)
        return process
    }
}
