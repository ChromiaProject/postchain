// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.extchains.ebft

import net.postchain.base.BaseApiInfrastructure
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.network.masterslave.slave.DefaultSlavePeersCommunicationConfigFactory

class SlaveExtChainsEbftInfrastructureFactory : BaseEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = SlaveEbftSyncInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext, DefaultSlavePeersCommunicationConfigFactory())

        val apiInfra = BaseApiInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext)
    }

}