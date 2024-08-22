package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerImageInfo
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.resources.ResourceLimitFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.managed.query.QueryRunner

open class BaseDirectoryDataSource(
        queryRunner: QueryRunner,
        appConfig: AppConfig,
) : BaseManagedNodeDataSource(queryRunner, appConfig), DirectoryDataSource {

    override fun getContainersToRun(): List<String>? = try {
        val res = query(
                "nm_get_containers",
                buildArgs("pubkey" to gtv(appConfig.pubKeyByteArray))
        )
        res.asArray().map { it.asString() }
    } catch (e: UserMistake) { // this can fail if we are the genesis node before having initialized the network, since we are not registered as node yet
        listOf()
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        return if (nmApiVersion >= 3) {
            query(
                    "nm_get_container_for_blockchain",
                    buildArgs("blockchain_rid" to gtv(brid.data))
            ).asString()
        } else {
            throw Exception("Directory1 v.$nmApiVersion doesn't support 'nm_get_container_for_blockchain' query")
        }
    }

    override fun getBlockchainContainersForNode(brid: BlockchainRid): List<String> {
        return if (nmApiVersion >= 14) {
            query(
                    "nm_get_blockchain_containers_for_node",
                    buildArgs("node_id" to gtv(appConfig.pubKeyByteArray), "blockchain_rid" to gtv(brid.data))
            ).asArray().map { it.asString() }
        } else {
            listOf(getContainerForBlockchain(brid))
        }
    }

    override fun getResourceLimitForContainer(container: String): ContainerResourceLimits {
        val resourceLimits = query(
                "nm_get_container_limits",
                buildArgs("name" to gtv(container))
        ).asDict().mapValues { (_, v) -> v.asInteger() }.mapNotNull {
            ResourceLimitFactory.fromPair(it.toPair())
        }.toTypedArray()

        return ContainerResourceLimits(*resourceLimits)
    }

    override fun getImageForContainer(container: String): ContainerImageInfo? {
        if (nmApiVersion < 20) return null

        val response = query(
                "nm_get_container_image",
                buildArgs("name" to gtv(container))
        )
        return if (response.isNull()) null else
            response.toObject<ContainerImageInfo>()
    }
}
