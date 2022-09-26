package net.postchain.d1.icmf

import net.postchain.PostchainContext

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
        IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createClientProvider() = PostchainClientMocks.createProvider()

    override fun createClusterManagement() = IcmfTestClusterManagement()
}
