package net.postchain.devtools.mminfra

import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.directory1.D1ClusterInfo

class MockDirectoryDataSource(nodeIndex: Int) : MockManagedNodeDataSource(nodeIndex), DirectoryDataSource {

    override fun getContainersToRun(): List<String>? {
        TODO("Not yet implemented")
    }

    override fun getBlockchainsForContainer(containerId: String): List<BlockchainRid>? {
        TODO("Not yet implemented")
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        TODO("Not yet implemented")
    }

    override fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits {
        TODO("Not yet implemented")
    }

    override fun setLimitsForContainer(containerId: String, ramLimit: Long, cpuQuota: Long) {
        TODO("Not yet implemented")
    }

    override fun getAllClusters(): List<D1ClusterInfo> {
        TODO("Not yet implemented")
    }
}