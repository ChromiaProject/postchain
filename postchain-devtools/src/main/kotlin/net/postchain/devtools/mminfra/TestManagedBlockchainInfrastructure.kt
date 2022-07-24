package net.postchain.devtools.mminfra

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.SynchronizationInfrastructure

class TestManagedBlockchainInfrastructure(postchainContext: PostchainContext,
                                          syncInfra: SynchronizationInfrastructure, apiInfra: ApiInfrastructure,
                                          val mockDataSource: MockManagedNodeDataSource) :
        BaseBlockchainInfrastructure(syncInfra, apiInfra, postchainContext) {
    override fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long,
    ): BlockchainConfiguration {

        return mockDataSource.getConf(rawConfigurationData)!!
    }
}