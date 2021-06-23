package net.postchain.el2.l2

import net.postchain.base.BaseApiInfrastructure
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.BaseEBFTInfrastructureFactory

class BaseL2EBFTInfrastructureFactory : BaseEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = L2EBFTSynchronizationInfrastructure(nodeConfigProvider, nodeDiagnosticContext)
        val apiInfra = BaseApiInfrastructure(nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
            nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext
        )
    }
}