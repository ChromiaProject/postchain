package net.postchain.managed

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.ebft.BaseEBFTInfrastructureFactory

class ManagedEBFTInfrastructureFactory : BaseEBFTInfrastructureFactory() {

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure
    ): BlockchainProcessManager {

        return ManagedBlockchainProcessManager(
                blockchainInfrastructure,
                nodeConfigProvider,
                ManagedBlockchainConfigurationProvider()
        )
    }
}