package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClientProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.DirectoryClusterManagement
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXBlockchainConfiguration

open class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, GlobalTopicIcmfReceiver>()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is GTXBlockchainConfiguration) {
            getIcmfRemoteSpecialTxExtension(configuration)?.let { txExt ->
                val topics = configuration.configData.rawConfig["icmf"]!!["receiver"]!!["topics"]!!.asArray().map { it.asString() }
                val clusterManagement = createClusterManagement()
                val receiver = GlobalTopicIcmfReceiver(topics, configuration.cryptoSystem, postchainContext.storage, configuration.chainID,
                        clusterManagement, createClientProvider())
                receivers[configuration.chainID] = receiver
                txExt.receiver = receiver
                txExt.clusterManagement = clusterManagement
            }
        }
    }

    open fun createClientProvider(): PostchainClientProvider = ConcretePostchainClientProvider()

    open fun createClusterManagement(): ClusterManagement = DirectoryClusterManagement { name, args -> GtvNull } // TODO createClusterManagement

    override fun disconnectProcess(process: BlockchainProcess) {
        receivers.remove(process.blockchainEngine.getConfiguration().chainID)?.shutdown()
    }

    override fun shutdown() {
        receivers.values.forEach { it.shutdown() }
    }

    private fun getIcmfRemoteSpecialTxExtension(cfg: GTXBlockchainConfiguration): IcmfRemoteSpecialTxExtension? {
        return cfg.module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is IcmfRemoteSpecialTxExtension)
        } as IcmfRemoteSpecialTxExtension?
    }
}
