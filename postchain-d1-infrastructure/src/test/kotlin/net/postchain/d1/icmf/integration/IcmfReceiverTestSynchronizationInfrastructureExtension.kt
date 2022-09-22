package net.postchain.d1.icmf.integration

import net.postchain.PostchainContext
import net.postchain.base.Storage
import net.postchain.d1.icmf.GlobalTopicIcmfReceiver
import net.postchain.d1.icmf.IcmfReceiverSynchronizationInfrastructureExtension
import net.postchain.gtx.GTXBlockchainConfiguration

class IcmfReceiverTestSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createReceiver(configuration: GTXBlockchainConfiguration, storage: Storage): GlobalTopicIcmfReceiver {
        return GlobalTopicIcmfReceiver(listOf("my-topic"), configuration.cryptoSystem, postchainContext.storage, configuration.chainID, PostchainClientMocks.createProvider())
    }
}
