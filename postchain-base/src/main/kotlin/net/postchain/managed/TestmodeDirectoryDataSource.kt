package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.Cpu
import net.postchain.containers.bpm.Ram
import net.postchain.containers.bpm.Storage
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.managed.query.QueryRunner

class TestmodeDirectoryDataSource(
        queryRunner: QueryRunner,
        appConfig: AppConfig,
        private val containerNodeConfig: ContainerNodeConfig,
) : BaseDirectoryDataSource(queryRunner, appConfig) {

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        return if (containerNodeConfig.testmode) {
            val short = brid.toHex().uppercase().take(8)
            containerNodeConfig.testmodeDappsContainers[short] ?: "cont0"
        } else {
            super.getContainerForBlockchain(brid)
        }
    }

    override fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits {
        return if (containerNodeConfig.testmode) {
            ContainerResourceLimits(
                    Cpu(containerNodeConfig.testmodeResourceLimitsCPU),
                    Ram(containerNodeConfig.testmodeResourceLimitsRAM),
                    Storage(containerNodeConfig.testmodeResourceLimitsSTORAGE)
            )
        } else {
            super.getResourceLimitForContainer(containerId)
        }
    }

}