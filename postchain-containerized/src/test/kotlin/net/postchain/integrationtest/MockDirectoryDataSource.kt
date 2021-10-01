package net.postchain.integrationtest

import net.postchain.core.BlockchainRid
import net.postchain.containers.infra.ContainerResourceType
import net.postchain.devtools.MockManagedNodeDataSource
import net.postchain.gtv.GtvFactory
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.MsMessageHandler


class MockDirectoryDataSource(nodeIndex: Int) : MockManagedNodeDataSource(nodeIndex), DirectoryDataSource {

    var ram = 7000_000_000L
    var cpu = 100_000L
    var subnodeInterceptors = mutableMapOf<BlockchainRid, TestPacketConsumer>()


    override fun getConfigurations(blockchainRidRaw: ByteArray): Map<Long, ByteArray> {
        val l = bridToConfs[BlockchainRid(blockchainRidRaw)] ?: return mapOf()
        val confs = mutableMapOf<Long, ByteArray>()
        for (entry in l) {
            val data = entry.value.second
            // try to decode to ensure data is valid
            GtvFactory.decodeGtv(data).asDict()
            confs[entry.key] = data
        }
        return confs
    }

    override fun getContainersToRun(): List<String>? {
        return listOf("system", firstContainerName, secondContainerName)
    }

    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        return blockchainDistribution[containerID]
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        var res = "system"
        blockchainDistribution.forEach { (containerName, bcList) ->
            val found = bcList.firstOrNull { it == brid } != null
            if (found) res = containerName
        }
        return res
    }

    override fun getResourceLimitForContainer(containerID: String): Map<ContainerResourceType, Long>? {
        if (containerID == firstContainerName) {
            return mapOf(ContainerResourceType.STORAGE to 10L, ContainerResourceType.RAM to ram,
                    ContainerResourceType.CPU to cpu)
        }
        return mapOf() //no limits for naked system container.
    }

    override fun setLimitsForContainer(containerID: String, ramLimit: Long, cpuQuota: Long) {
        if (containerID == firstContainerName) {
            ram = ramLimit
            cpu = cpuQuota
        }
    }

    fun getSubnodeInterceptor(subconsumer: MsMessageHandler, blockchainRid: BlockchainRid): TestPacketConsumer {
        subnodeInterceptors.put(blockchainRid, TestPacketConsumer(subconsumer)) // the real one
        return subnodeInterceptors[blockchainRid]!!
    }
}
