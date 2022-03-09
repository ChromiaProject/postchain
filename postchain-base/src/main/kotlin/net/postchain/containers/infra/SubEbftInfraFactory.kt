// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.SubNodeBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.InfrastructureFactory
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.network.mastersub.subnode.DefaultSubConnectionManager
import net.postchain.network.mastersub.subnode.DefaultSubPeersCommConfigFactory
import net.postchain.network.mastersub.subnode.SubConnectionManager

class SubEbftInfraFactory : InfrastructureFactory {

    lateinit var subConnectionManager: SubConnectionManager

    protected fun getOrCreateSubConnectionManager(nodeConfig: NodeConfig): SubConnectionManager {
        if (!::subConnectionManager.isInitialized) {
            subConnectionManager = DefaultSubConnectionManager(nodeConfig)
        }
        return subConnectionManager
    }
    
    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val connectionManager = getOrCreateSubConnectionManager(nodeConfigProvider.getConfiguration())
        val syncInfra = EBFTSynchronizationInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext, connectionManager, DefaultSubPeersCommConfigFactory())

        val apiInfra = BaseApiInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext, connectionManager)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {
        val connectionManager = getOrCreateSubConnectionManager(nodeConfigProvider.getConfiguration())
        return SubNodeBlockchainProcessManager(blockchainInfrastructure, nodeConfigProvider, blockchainConfigurationProvider, nodeDiagnosticContext, connectionManager)
    }

}