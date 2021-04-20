// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedEBFTInfrastructureFactory

open class MasterManagedEbftInfraFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = DefaultMasterSyncInfra(
                nodeConfigProvider, nodeDiagnosticContext)

        val apiInfra = DefaultMasterApiInfra(
                nodeConfigProvider, nodeDiagnosticContext)

        return DefaultMasterBlockchainInfra(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {

        return ContainerManagedBlockchainProcessManager(
                blockchainInfrastructure as MasterBlockchainInfra,
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext)
    }
}