package net.postchain.managed

import net.postchain.config.node.NodeConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.ContainerResourceLimits.Companion.CPU_KEY
import net.postchain.containers.bpm.ContainerResourceLimits.Companion.RAM_KEY
import net.postchain.containers.bpm.ContainerResourceLimits.Companion.STORAGE_KEY
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainRid
import net.postchain.gtv.GtvFactory

class BaseDirectoryDataSource(
        queries: BlockQueries,
        nodeConfig: NodeConfig
) : BaseManagedNodeDataSource(queries, nodeConfig),
        DirectoryDataSource {

    override fun getContainersToRun(): List<String>? {
        val res = queries.query(
                "nm_get_containers",
                buildArgs("pubkey" to GtvFactory.gtv(nodeConfig.pubKeyByteArray))
        ).get()

        return res.asArray().map { it.asString() }
    }

    override fun getBlockchainsForContainer(containerId: String): List<BlockchainRid>? {
        val res = queries.query(
                "nm_get_blockchains_for_container",
                buildArgs("container_id" to GtvFactory.gtv(containerId))
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

    // TODO: [et]: directory vs containerId?
    override fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits {
        return if (nodeConfig.containersTestmode) {
            ContainerResourceLimits(
                    nodeConfig.containersTestmodeResourceLimitsRAM,
                    nodeConfig.containersTestmodeResourceLimitsCPU,
                    nodeConfig.containersTestmodeResourceLimitsSTORAGE
            )
        } else {
            val queryReply = queries.query(
                    "nm_get_container_limits",
                    buildArgs("container_id" to GtvFactory.gtv(containerId))
            ).get().asDict()

            ContainerResourceLimits(
                    queryReply[RAM_KEY]?.asInteger(),
                    queryReply[CPU_KEY]?.asInteger(),
                    queryReply[STORAGE_KEY]?.asInteger()
            )
        }
    }

    override fun setLimitsForContainer(containerId: String, ramLimit: Long, cpuQuota: Long) {
        TODO("Will not be used")
    }
}
