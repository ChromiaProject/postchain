package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.resources.ResourceLimitFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.managed.query.QueryRunner

open class BaseDirectoryDataSource(
        queryRunner: QueryRunner,
        appConfig: AppConfig,
) : BaseManagedNodeDataSource(queryRunner, appConfig), DirectoryDataSource {

    override fun getContainersToRun(): List<String>? {
        val res = query(
                "nm_get_containers",
                buildArgs("pubkey" to gtv(appConfig.pubKeyByteArray))
        )

        return res.asArray().map { it.asString() }
    }

    override fun getBlockchainsForContainer(containerId: String): List<BlockchainRid>? {
        val res = query(
                "nm_get_blockchains_for_container",
                buildArgs("container_id" to gtv(containerId))
        )

        return res.asArray().map { BlockchainRid(it.asByteArray()) }
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        return if (nmApiVersion >= 3) {
            query(
                    "nm_get_container_for_blockchain",
                    buildArgs("blockchain_rid" to gtv(brid.data))
            ).asString()
        } else {
            throw Exception("Directory1 v.{$nmApiVersion} doesn't support 'nm_get_container_for_blockchain' query")
        }
    }

    override fun getContainersForBlockchain(brid: BlockchainRid): List<String> {
        return if (nmApiVersion >= 99) {
            query(
                    "nm_get_containers_for_blockchain",
                    buildArgs("blockchain_rid" to gtv(brid.data))
            ).asArray().map { it.asString() }
        } else {
            throw Exception("Directory1 v.{$nmApiVersion} doesn't support 'nm_get_containers_for_blockchain' query")
        }
    }

    // TODO: [et]: directory vs containerId?
    override fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits {
        val resourceLimits = query(
                "nm_get_container_limits",
                buildArgs("name" to gtv(containerId))
        ).asDict().mapValues { (_, v) -> v.asInteger() }.mapNotNull {
            ResourceLimitFactory.fromPair(it.toPair())
        }.toTypedArray()

        return ContainerResourceLimits(*resourceLimits)
    }
}
