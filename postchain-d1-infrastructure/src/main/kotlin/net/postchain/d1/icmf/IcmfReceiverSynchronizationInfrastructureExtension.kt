package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.gtx.GTXBlockchainConfiguration

class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, GlobalTopicIcmfReceiver>()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is GTXBlockchainConfiguration) {
            val topics = configuration.configData.rawConfig["icmf"]!!["receiver"]!!["topics"]!!.asArray().map { it.asString() }
            val receiver = GlobalTopicIcmfReceiver(topics, configuration.cryptoSystem, postchainContext.storage, configuration.chainID)
            receivers[configuration.chainID] = receiver

            getIcmfRemoteSpecialTxExtension(configuration)?.receiver = receiver
        }
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
