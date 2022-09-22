package net.postchain.d1.icmf.integration

import net.postchain.PostchainContext
import net.postchain.d1.icmf.IcmfReceiverSynchronizationInfrastructureExtension

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
        IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createClientProvider() = PostchainClientMocks.createProvider()

    override fun createClusterManagement() = IcmfTestClusterManagement()
}
