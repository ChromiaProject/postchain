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

open class TestManagedEBFTInfrastructureFactory : ManagedEBFTInfrastructureFactory() {

    lateinit var nodeConfig: NodeConfig
    lateinit var dataSource: MockManagedNodeDataSource

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider): BlockchainProcessManager {
        return TestManagedBlockchainProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigurationProvider, dataSource)
    }

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            dataSource = appConfig.getProperty("infrastructure.datasource") as MockManagedNodeDataSource

            val mockBlockQueriesProvider = configurationProvider as MockBlockchainConfigurationProvider
            mockBlockQueriesProvider.dataSource = dataSource

            val syncInfra = EBFTSynchronizationInfrastructure(this)
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val apiInfra = BaseApiInfrastructure(restApiConfig, nodeDiagnosticContext, debug, postchainContext)
            return TestManagedBlockchainInfrastructure(this, syncInfra, apiInfra, dataSource)
        }
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider =
            MockBlockchainConfigurationProvider()
}
