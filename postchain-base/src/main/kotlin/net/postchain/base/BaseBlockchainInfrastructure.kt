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
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory

open class BaseBlockchainInfrastructure(
        private val nodeConfigProvider: NodeConfigurationProvider,
        val defaultSynchronizationInfrastructure: SynchronizationInfrastructure,
        val apiInfrastructure: ApiInfrastructure,
        val nodeDiagnosticContext: NodeDiagnosticContext
) : BlockchainInfrastructure {

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

        // TODO: [et]: Maybe extract 'queuecapacity' param from ''
        val transactionQueue = BaseTransactionQueue(
                (configuration as BaseBlockchainConfiguration)
                        .configData.getBlockBuildingStrategy()?.get("queuecapacity")?.asInteger()?.toInt() ?: 2500)

        return BaseBlockchainEngine(processName, configuration, storage, configuration.chainID, transactionQueue)
                .apply {
                    setRestartHandler(restartHandler)
                    initialize()
                }
    }

    fun getSynchronizationInfrastucture(name: String?): SynchronizationInfrastructure {
        if (name == null) return defaultSynchronizationInfrastructure
        val full_name = if (name == "ebft") "net.postchain.ebft.EBFTSynchronizationInfrastructure" else name
        if (full_name in syncInfraCache) return syncInfraCache[full_name]!!
        val iClass = Class.forName(full_name)
        val ctor = iClass.getConstructor(
                NodeConfigurationProvider::class.java,
                NodeDiagnosticContext::class.java)
        val infra = ctor.newInstance(nodeConfigProvider, nodeDiagnosticContext) as SynchronizationInfrastructure
        syncInfraCache[full_name] = infra
        return infra
    }

    fun getSynchronizationInfrastuctureExtension(name: String): SynchronizationInfrastructureExtension {
        if (name in syncInfraCache) return syncInfraExtCache[name]!!
        val iClass = Class.forName(name)
        val ctor = iClass.getConstructor(
                NodeConfigurationProvider::class.java,
                NodeDiagnosticContext::class.java)
        val infra = ctor.newInstance(nodeConfigProvider, nodeDiagnosticContext) as SynchronizationInfrastructureExtension
        syncInfraExtCache[name] = infra
        return infra
    }

    override fun makeBlockchainProcess(processName: BlockchainProcessName, engine: BlockchainEngine,
                                       historicBlockchainContext: HistoricBlockchainContext?): BlockchainProcess {
        val conf = engine.getConfiguration()
        val synchronizationInfrastructure = getSynchronizationInfrastucture(
                if (conf is BaseBlockchainConfiguration) conf.configData.getSyncInfrastructureName()
                else null
        )
        val process = synchronizationInfrastructure.makeBlockchainProcess(processName, engine, historicBlockchainContext)
        if (conf is BaseBlockchainConfiguration) {
            for (extName in conf.configData.getSyncInfrastructureExtensions()) {
                getSynchronizationInfrastuctureExtension(extName).connectProcess(process)
            }
        }
        apiInfrastructure.connectProcess(process)
        return process
    }
}
