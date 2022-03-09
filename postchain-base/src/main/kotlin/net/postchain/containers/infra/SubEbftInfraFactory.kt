// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
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
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.subnode.DefaultSubConnectionManager
import net.postchain.network.mastersub.subnode.DefaultSubPeersCommConfigFactory
import net.postchain.network.mastersub.subnode.SubConnectionManager

class SubEbftInfraFactory : InfrastructureFactory {

    override fun makeConnectionManager(nodeConfigProvider: NodeConfigurationProvider): SubConnectionManager {
        return DefaultSubConnectionManager(nodeConfigProvider.getConfiguration())
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(
            postchainContext: PostchainContext
    ): BlockchainInfrastructure {
        with(postchainContext) {
            val syncInfra = EBFTSynchronizationInfrastructure(
                    nodeConfig, nodeDiagnosticContext, connectionManager, DefaultSubPeersCommConfigFactory())

            val apiInfra = BaseApiInfrastructure(
                    nodeConfig, nodeDiagnosticContext)

            return BaseBlockchainInfrastructure(
                    nodeConfig, syncInfra, apiInfra, nodeDiagnosticContext, connectionManager)
        }
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext,
            connectionManager: ConnectionManager
    ): BlockchainProcessManager {
        return SubNodeBlockchainProcessManager(blockchainInfrastructure, nodeConfigProvider, blockchainConfigurationProvider, nodeDiagnosticContext, connectionManager as SubConnectionManager)
    }

}