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
            val containerNodeConfig = ContainerNodeConfig.fromAppConfig(nodeConfig.appConfig)
            val restApiConfig = RestApiConfig.fromAppConfig(nodeConfig.appConfig)
            val syncInfra = DefaultMasterSyncInfra(this, DefaultMasterConnectionManager(nodeConfig, containerNodeConfig), containerNodeConfig)
            val apiInfra = DefaultMasterApiInfra(restApiConfig, nodeDiagnosticContext, containerNodeConfig)

            return DefaultMasterBlockchainInfra(this, syncInfra, apiInfra)
        }
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {

        return ContainerManagedBlockchainProcessManager(
                postchainContext,
                blockchainInfrastructure as MasterBlockchainInfra,
                blockchainConfigurationProvider
        )
    }
}