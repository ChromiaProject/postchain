package net.postchain.managed

import net.postchain.config.node.NodeConfig
import net.postchain.containers.infra.ContainerResourceType
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainRid
import net.postchain.gtv.GtvFactory

class BaseDirectoryDataSource(queries: BlockQueries, nodeConfig: NodeConfig) :
    BaseManagedNodeDataSource(queries, nodeConfig), DirectoryDataSource {

    override fun getContainersToRun(): List<String>? {
        val res = queries.query(
            "nm_get_containers",
            buildArgs("pubkey" to GtvFactory.gtv(nodeConfig.pubKeyByteArray))
        ).get()

        return res.asArray().map { it.asString() }
    }

    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        val res = queries.query(
            "nm_get_blockchains_for_container",
            buildArgs("container_id" to GtvFactory.gtv(containerID))
        ).get()

        return res.asArray().map { BlockchainRid(it.asByteArray()) }
    }

    // TODO: [et]: Test implementation. Fix it.
    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        //val num = Integer.parseInt(brid.toHex().takeLast(1), 16) / 6 // 3 containers
        //return "ps$num"

        val short = brid.toHex().toUpperCase().take(8)
        return nodeConfig.dappsContainers[short] ?: "cont0"
    }

    override fun getResourceLimitForContainer(containerID: String): Map<ContainerResourceType, Long>? {
        /*
        val queryReply = queries.query(
                "nm_get_container_limits",
                buildArgs("container_id" to GtvFactory.gtv(containerID))
        ).get().asDict()
        val resList = queryReply.map { ContainerResourceType.valueOf(it.key.toUpperCase()) to it.value.asInteger() }.toMap()
        return resList
         */

        // TODO: [POS-164]: Implement Container resources management
        return emptyMap()
    }

    override fun setLimitsForContainer(containerID: String, ramLimit: Long, cpuQuota: Long) {
        TODO("Will not be used")
    }
}
