package net.postchain.devtools.mminfra

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.core.*

class TestManagedBlockchainInfrastructure(
        postchainContext: PostchainContext,
        syncInfra: SynchronizationInfrastructure,
        apiInfra: ApiInfrastructure,
        val mockDataSource: MockManagedNodeDataSource
) : BaseBlockchainInfrastructure(syncInfra, apiInfra, postchainContext) {
    override fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long,
            bcConfigurationFactory: (String) -> BlockchainConfigurationFactory
    ): BlockchainConfiguration {
        return mockDataSource.getBuiltConfiguration(chainId, rawConfigurationData)
    }
}