package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.ContainerResourceLimits.ResourceLimit
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.gtv.GtvFactory
import net.postchain.managed.query.QueryRunner

class BaseDirectoryDataSource(
        queryRunner: QueryRunner,
        appConfig: AppConfig,
        private val containerNodeConfig: ContainerNodeConfig,
) : BaseManagedNodeDataSource(queryRunner, appConfig), DirectoryDataSource {

    override fun getContainersToRun(): List<String>? {
        val res = query(
                "nm_get_containers",
                buildArgs("pubkey" to GtvFactory.gtv(appConfig.pubKeyByteArray))
        )

        return res.asArray().map { it.asString() }
    }

    override fun getBlockchainsForContainer(containerId: String): List<BlockchainRid>? {
        val res = query(
                "nm_get_blockchains_for_container",
                buildArgs("container_id" to GtvFactory.gtv(containerId))
        )

        return res.asArray().map { BlockchainRid(it.asByteArray()) }
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        return if (containerNodeConfig.testmode) {
            val short = brid.toHex().uppercase().take(8)
            containerNodeConfig.testmodeDappsContainers[short] ?: "cont0"
        } else {
            if (nmApiVersion >= 3) {
                query(
                        "nm_get_container_for_blockchain",
                        buildArgs("blockchain_rid" to GtvFactory.gtv(brid.data))
                ).asString()
            } else {
                throw Exception("Directory1 v.{$nmApiVersion} doesn't support 'nm_get_container_for_blockchain' query")
            }
        }
    }

    // TODO: [et]: directory vs containerId?
    override fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits {
        return if (containerNodeConfig.testmode) {
            ContainerResourceLimits.fromValues(
                    containerNodeConfig.testmodeResourceLimitsRAM,
                    containerNodeConfig.testmodeResourceLimitsCPU,
                    containerNodeConfig.testmodeResourceLimitsSTORAGE
            )
        } else {
            val resourceLimits = query(
                    "nm_get_container_limits",
                    buildArgs("name" to GtvFactory.gtv(containerId))
            ).asDict()
                    .map { ResourceLimit.valueOf(it.key.uppercase()) to it.value.asInteger() }
                    .toMap()

            ContainerResourceLimits(resourceLimits)
        }
    }

    override fun setLimitsForContainer(containerId: String, ramLimit: Long, cpuQuota: Long) {
        TODO("Will not be used")
    }
}
