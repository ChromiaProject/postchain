package net.postchain.devtools.mminfra.pcu

import net.postchain.PostchainContext
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.devtools.mminfra.TestManagedBlockchainInfrastructure
import net.postchain.devtools.mminfra.TestManagedEBFTInfrastructureFactory
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.managed.PcuManagedBlockchainConfigurationProvider

open class TestPcuManagedEBFTInfrastructureFactory : TestManagedEBFTInfrastructureFactory() {

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {
        return TestPcuManagedBlockchainProcessManager(
                postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                dataSource
        )
    }

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            dataSource = appConfig.getProperty("infrastructure.datasource") as MockPcuManagedNodeDataSource
            (configurationProvider as PcuManagedBlockchainConfigurationProvider).setManagedDataSource(dataSource)

            val syncInfra = EBFTSynchronizationInfrastructure(this)
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val apiInfra = BaseApiInfrastructure(restApiConfig, nodeDiagnosticContext, configurationProvider, debug)
            return TestManagedBlockchainInfrastructure(this, syncInfra, apiInfra, dataSource)
        }
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return TestPcuManagedBlockchainConfigurationProvider()
    }
}