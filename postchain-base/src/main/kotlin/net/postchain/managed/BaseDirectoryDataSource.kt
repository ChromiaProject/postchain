package net.postchain.managed

import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockQueries
import net.postchain.gtv.GtvFactory

class BaseDirectoryDataSource(queries: BlockQueries, nodeConfig: NodeConfig) : BaseManagedNodeDataSource(queries, nodeConfig), DirectoryDataSource {


    override fun getContainersToRun(): List<String>? {
        val res = queries.query("nm_get_containers",
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

    override fun getContainerForBlockchain(brid: BlockchainRid): String? {
        TODO("Not yet implemented")
    }

    override fun getResourceLimitForContainer(containerID: String): Map<String, Long>? {
        val queryReply = queries.query(
                "nm_get_container_limits",
                buildArgs("container_id" to GtvFactory.gtv(containerID))
        ).get().asDict()
        val resList = queryReply.map { it.key to it.value.asInteger() }.toMap()
        return resList
    }
}
