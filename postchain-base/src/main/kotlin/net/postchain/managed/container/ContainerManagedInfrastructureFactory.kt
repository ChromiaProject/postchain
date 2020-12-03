package net.postchain.managed.container

import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedEBFTInfrastructureFactory

class ContainerManagedInfrastructureFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {

        return ContainerManagedBlockchainProcessManager(
                blockchainInfrastructure,
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext)
    }
}