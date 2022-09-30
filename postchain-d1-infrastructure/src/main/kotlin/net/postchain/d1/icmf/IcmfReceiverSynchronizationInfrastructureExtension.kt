package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClientProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.DirectoryClusterManagement
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedBlockchainConfiguration

open class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, GlobalTopicIcmfReceiver>()
    private val dbOperations = IcmfDatabaseOperationsImpl()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is ManagedBlockchainConfiguration) {
            getIcmfReceiverSpecialTxExtension(configuration)?.let { txExt ->
                val topics = configuration.configData.rawConfig["icmf"]!!["receiver"]!!["topics"]!!.asArray().map { it.asString() }
                val clusterManagement = createClusterManagement(configuration)
                val receiver = GlobalTopicIcmfReceiver(topics,
                        configuration.cryptoSystem,
                        engine.storage,
                        configuration.chainID,
                        clusterManagement,
                        createClientProvider(),
                        dbOperations
                )
                receivers[configuration.chainID] = receiver
                txExt.receiver = receiver
                txExt.clusterManagement = clusterManagement
            }
        }
    }

    open fun createClientProvider(): PostchainClientProvider = ConcretePostchainClientProvider()

    open fun createClusterManagement(configuration: ManagedBlockchainConfiguration): ClusterManagement =
            DirectoryClusterManagement(configuration.dataSource::query)

    override fun disconnectProcess(process: BlockchainProcess) {
        receivers.remove(process.blockchainEngine.getConfiguration().chainID)?.shutdown()
    }

    override fun shutdown() {
        receivers.values.forEach { it.shutdown() }
    }

    private fun getIcmfReceiverSpecialTxExtension(cfg: GTXBlockchainConfiguration): IcmfReceiverSpecialTxExtension? {
        return cfg.module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is IcmfReceiverSpecialTxExtension)
        } as IcmfReceiverSpecialTxExtension?
    }
}
