// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.config.blockchain.BlockchainConfigurationProvider

open class PcuManagedEBFTInfrastructureFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return PcuManagedBlockchainConfigurationProvider()
    }
}