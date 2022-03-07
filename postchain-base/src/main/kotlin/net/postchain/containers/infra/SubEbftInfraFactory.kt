// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.SubNodeBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.InfrastructureFactory
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.network.mastersub.subnode.DefaultSubPeersCommConfigFactory

class SubEbftInfraFactory : InfrastructureFactory {
    
    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = DefaultSubSyncInfra(
                nodeConfigProvider, nodeDiagnosticContext, DefaultSubPeersCommConfigFactory())

        val apiInfra = BaseApiInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {
        return SubNodeBlockchainProcessManager(blockchainInfrastructure, nodeConfigProvider, blockchainConfigurationProvider, nodeDiagnosticContext)
    }

}