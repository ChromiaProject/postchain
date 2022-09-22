package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.base.Storage
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.gtx.GTXBlockchainConfiguration

open class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, GlobalTopicIcmfReceiver>()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is GTXBlockchainConfiguration) {
            getIcmfRemoteSpecialTxExtension(configuration)?.apply {
                val newReceiver = createReceiver(configuration, postchainContext.storage)
                receivers[configuration.chainID] = newReceiver
                receiver = newReceiver
            }
        }
    }

    protected open fun createReceiver(configuration: GTXBlockchainConfiguration, storage: Storage): GlobalTopicIcmfReceiver {
        val topics = configuration.configData.rawConfig["icmf"]!!["receiver"]!!["topics"]!!.asArray().map { it.asString() }
        return GlobalTopicIcmfReceiver(topics, configuration.cryptoSystem, postchainContext.storage, configuration.chainID, ConcretePostchainClientProvider())
    }

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
