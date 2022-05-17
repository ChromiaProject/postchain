package net.postchain.containers.infra

import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.FileSystem

/**
 * Container chains configuration
 *
 * @param masterHost Used by subnode to connect to master for inter-node communication
 * @param slaveHost Used by master for restAPI communication with subnode
 * @param runningContainersCheckPeriod In number of blocks of chain0, set 0 to disable a check
 */
data class ContainerNodeConfig(
        val containerImage: String,
        val subnodeRestApiPort: Int,
        val masterHost: String,
        val masterPort: Int,
        val slaveHost: String,
        val containerSendConnectedPeersPeriod: Long,
        val runningContainersAtStartRegexp: String,
        val runningContainersCheckPeriod: Int,
        // Container FileSystem
        val containerFilesystem: String,
        val containerZfsPool: String,
        val containerZfsPoolInitScript: String,
        val containerBindPgdataVolume: Boolean,
        val containersTestmode: Boolean,
        val containersTestmodeResourceLimitsRAM: Long,
        val containersTestmodeResourceLimitsCPU: Long,
        val containersTestmodeResourceLimitsSTORAGE: Long,
        val dappsContainers: Map<String, String>
) {
    companion object {
        const val DEFAULT_PORT: Int = 9870
        const val CONTAINER_ZFS_INIT_SCRIPT = "container-zfs-init-script.sh"

        @JvmStatic
        fun fromAppConfig(config: AppConfig): ContainerNodeConfig {
            return ContainerNodeConfig(
                    config.getString("containerChains.dockerImage", "chromaway/postchain-subnode:latest"),
                    config.getInt("containerChains.api.port", 7740),
                    config.getString("containerChains.masterHost", "localhost"),
                    config.getInt("containerChains.masterPort", 9860),
                    config.getString("containerChains.slaveHost", "localhost"),
                    config.getLong("container.send-connected-peers-period", 60_000L),
                    config.getString("container.healthcheck.runningContainersAtStartRegexp", ""),
                    config.getInt("container.healthcheck.runningContainersCheckPeriod", 0),
                    config.getString("container.filesystem", FileSystem.Type.LOCAL.name).uppercase(), // LOCAL | ZFS
                    config.getString("container.zfs.pool-name", FileSystem.ZFS_POOL_NAME),
                    config.getString("container.zfs.pool-init-script", CONTAINER_ZFS_INIT_SCRIPT),
                    config.getBoolean("container.bind-pgdata-volume", true),
                    config.getBoolean("container.testmode", false),
                    config.getLong("container.testmode.resource-limits-ram", -1),
                    config.getLong("container.testmode.resource-limits-cpu", -1),
                    config.getLong("container.testmode.resource-limits-storage", -1),
                    initDappsContainers(config)
            )
        }

        /*
            Example:
                cont0=331A9436,B99F6E8B,BB73F0CA
                cont1=323ECC01,A9599992,86E2043D
                cont2=EB7387FD,94F29578
                cont3=0B942264
        */
        private fun initDappsContainers(config: AppConfig): Map<String, String> {
            val res = mutableMapOf<String, String>()

            val processCont: (String) -> Unit = { cont ->
                config.getStringArray(cont).forEach { res[it] = cont }
            }

            processCont("cont0")
            processCont("cont1")
            processCont("cont2")
            processCont("cont3")

            return res
        }
    }
}
