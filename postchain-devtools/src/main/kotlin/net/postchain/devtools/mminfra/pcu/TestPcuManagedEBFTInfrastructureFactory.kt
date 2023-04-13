package net.postchain.devtools.mminfra.pcu

import net.postchain.PostchainContext
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.devtools.mminfra.TestManagedBlockchainInfrastructure
import net.postchain.devtools.mminfra.TestManagedEBFTInfrastructureFactory
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.managed.ManagedBlockchainConfigurationProvider

open class TestPcuManagedEBFTInfrastructureFactory : TestManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            dataSource = appConfig.getProperty("infrastructure.datasource") as MockPcuManagedNodeDataSource
            (configurationProvider as ManagedBlockchainConfigurationProvider).setManagedDataSource(dataSource)

            val syncInfra = EBFTSynchronizationInfrastructure(this)
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val apiInfra = BaseApiInfrastructure(restApiConfig, nodeDiagnosticContext, debug, postchainContext)
            return TestManagedBlockchainInfrastructure(this, syncInfra, apiInfra, dataSource)
        }
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return TestPcuManagedBlockchainConfigurationProvider()
    }
}