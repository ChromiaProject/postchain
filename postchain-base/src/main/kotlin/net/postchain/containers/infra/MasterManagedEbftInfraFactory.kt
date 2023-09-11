// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.bpm.ContainerEnvironment
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.managed.ManagedEBFTInfrastructureFactory
import net.postchain.network.mastersub.master.DefaultMasterConnectionManager

open class MasterManagedEbftInfraFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            ContainerEnvironment.init(appConfig)
            val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val connectionManager = DefaultMasterConnectionManager(appConfig, containerNodeConfig, postchainContext.blockQueriesProvider)
            val syncInfra = DefaultMasterSyncInfra(this, connectionManager, containerNodeConfig)
            val apiInfra = DefaultMasterApiInfra(restApiConfig, nodeDiagnosticContext, postchainContext)

            return DefaultMasterBlockchainInfra(this, syncInfra, apiInfra)
        }
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
    ): BlockchainProcessManager {
        val blockchainProcessManager = ContainerManagedBlockchainProcessManager(
                postchainContext,
                blockchainInfrastructure as MasterBlockchainInfra,
                blockchainConfigurationProvider,
                getProcessManagerExtensions(postchainContext, blockchainInfrastructure)
        )
        (blockchainInfrastructure as DefaultMasterBlockchainInfra).registerAfterSubnodeCommitListener(blockchainProcessManager)

        return blockchainProcessManager
    }
}