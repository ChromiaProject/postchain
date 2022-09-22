package net.postchain.devtools.mminfra

import net.postchain.PostchainContext
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.devtools.MockBlockchainConfigurationProvider
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.managed.ManagedEBFTInfrastructureFactory

class TestManagedEBFTInfrastructureFactory : ManagedEBFTInfrastructureFactory() {
    lateinit var nodeConfig: NodeConfig
    lateinit var directoryDataSource: MockDirectoryDataSource
    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider): BlockchainProcessManager {
        return TestManagedBlockchainProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigurationProvider, directoryDataSource)
    }

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            directoryDataSource = appConfig.getProperty("infrastructure.datasource") as MockDirectoryDataSource

            val syncInfra = EBFTSynchronizationInfrastructure(this)
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val apiInfra = BaseApiInfrastructure(restApiConfig, nodeDiagnosticContext)
            return TestManagedBlockchainInfrastructure(this, syncInfra, apiInfra, directoryDataSource)
        }
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return MockBlockchainConfigurationProvider(directoryDataSource)
    }
}