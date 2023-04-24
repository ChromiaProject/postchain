package net.postchain.devtools.mminfra.pcu

import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.devtools.MockBlockchainConfigurationProvider
import net.postchain.devtools.mminfra.TestManagedEBFTInfrastructureFactory

open class TestPcuManagedEBFTInfrastructureFactory : TestManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return MockBlockchainConfigurationProvider(true)
    }
}