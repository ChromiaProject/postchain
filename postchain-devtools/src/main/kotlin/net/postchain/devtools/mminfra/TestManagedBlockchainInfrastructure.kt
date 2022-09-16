package net.postchain.devtools.mminfra

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.gtx.ModuleInitializer

class TestManagedBlockchainInfrastructure(
        postchainContext: PostchainContext,
        syncInfra: SynchronizationInfrastructure,
        apiInfra: ApiInfrastructure,
        val mockDataSource: MockDirectoryDataSource
) : BaseBlockchainInfrastructure(syncInfra, apiInfra, postchainContext) {
    override fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long,
            moduleInitializer: ModuleInitializer
    ): BlockchainConfiguration {
        return mockDataSource.getBuiltConfiguration(chainId, rawConfigurationData)
    }
}