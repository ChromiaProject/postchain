package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.managed.ManagedBlockchainConfiguration

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
        IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createClientProvider() = PostchainClientMocks.createProvider()

    override fun createClusterManagement(configuration: ManagedBlockchainConfiguration) = IcmfTestClusterManagement()
}
