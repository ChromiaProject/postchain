// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.managed.ManagedEBFTInfrastructureFactory
import net.postchain.network.mastersub.master.DefaultMasterConnectionManager

open class MasterManagedEbftInfraFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val connectionManager = DefaultMasterConnectionManager(appConfig, containerNodeConfig)
            val syncInfra = DefaultMasterSyncInfra(this, connectionManager, containerNodeConfig)
            val apiInfra = DefaultMasterApiInfra(restApiConfig, nodeDiagnosticContext)

            return DefaultMasterBlockchainInfra(this, syncInfra, apiInfra)
        }
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
    ): BlockchainProcessManager {

        return ContainerManagedBlockchainProcessManager(
                postchainContext,
                blockchainInfrastructure as MasterBlockchainInfra,
                blockchainConfigurationProvider
        )
    }
}