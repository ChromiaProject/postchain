package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
        IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createClientProvider() = PostchainClientMocks.createProvider()

    override fun createClusterManagement(configuration: BlockchainConfiguration) = IcmfTestClusterManagement()
}
