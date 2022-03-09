// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedEBFTInfrastructureFactory
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.master.DefaultMasterConnectionManager

open class MasterManagedEbftInfraFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(
            postchainContext: PostchainContext
    ): BlockchainInfrastructure {
        with(postchainContext) {
            val syncInfra = DefaultMasterSyncInfra(
                    nodeConfig, nodeDiagnosticContext, DefaultMasterConnectionManager(nodeConfig), connectionManager)

            val apiInfra = DefaultMasterApiInfra(
                    nodeConfig, nodeDiagnosticContext)

            return DefaultMasterBlockchainInfra(
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

        return ContainerManagedBlockchainProcessManager(
                blockchainInfrastructure as MasterBlockchainInfra,
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext,
                connectionManager
        )
    }
}