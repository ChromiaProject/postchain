// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.containers.bpm.SubNodeBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.InfrastructureFactory
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.network.mastersub.subnode.DefaultSubConnectionManager
import net.postchain.network.mastersub.subnode.DefaultSubPeersCommConfigFactory
import net.postchain.network.mastersub.subnode.SubConnectionManager

class SubEbftInfraFactory : InfrastructureFactory {

    override fun makeConnectionManager(appConfig: AppConfig): SubConnectionManager {
        val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
        return DefaultSubConnectionManager(appConfig, containerNodeConfig)
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            val syncInfra = EBFTSynchronizationInfrastructure(this, DefaultSubPeersCommConfigFactory())
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val apiInfra = BaseApiInfrastructure(restApiConfig, nodeDiagnosticContext, configurationProvider)

            return BaseBlockchainInfrastructure(syncInfra, apiInfra, this)
        }
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {
        return SubNodeBlockchainProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigurationProvider)
    }

}