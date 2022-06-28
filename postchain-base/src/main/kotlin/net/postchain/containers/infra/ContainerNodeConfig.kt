package net.postchain.containers.infra

import net.postchain.config.app.AppConfig
import net.postchain.config.app.Config
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

    /**
     * A dir in host filesystem where container volume will be created.
     * [net.postchain.containers.bpm.ContainerConfigFactory] uses it to create a docker volume for subnode container.
     *
     * If master node is launched natively or by means of Testcontainers Lib or CI/CD,
     * [hostMountDir] has to be equal to [masterMountDir]
     *
     * If master node is launched inside a container, i.e. in case of DinD,
     * [hostMountDir] might not be equal to [masterMountDir]
     */
    val hostMountDir: String,

    /**
     * A path to dir where container volume is placed in the master (container) filesystem.
     * [net.postchain.containers.bpm.ContainerInitializer] uses it to create container node config file,
     * blockchains dir, etc.
     *
     * If master node is launched natively or by means of Testcontainers Lib or CI/CD,
     * [hostMountDir] has to be equal to [masterMountDir]
     *
     * If master node is launched inside a container, i.e. in case of DinD,
     * [hostMountDir] might not be equal to [masterMountDir]
     */
    val masterMountDir: String,
    val containerZfsPool: String,
    val containerZfsPoolInitScript: String,
    val containerBindPgdataVolume: Boolean,
    val containersTestmode: Boolean,
    val containersTestmodeResourceLimitsRAM: Long,
    val containersTestmodeResourceLimitsCPU: Long,
    val containersTestmodeResourceLimitsSTORAGE: Long,
    val testmodeDappsContainers: Map<String, String>
) : Config {
    companion object {
        const val DEFAULT_PORT: Int = 9870
        const val CONTAINER_ZFS_INIT_SCRIPT = "container-zfs-init-script.sh"

        @JvmStatic
        fun fromAppConfig(config: AppConfig): ContainerNodeConfig {
            return ContainerNodeConfig(
                config.getString("container.dockerImage", "chromaway/postchain-subnode:latest"),
                config.getInt("container.api.port", 7740),
                config.getString("container.masterHost", "localhost"),
                config.getInt("container.masterPort", 9860),
                config.getString("container.slaveHost", "localhost"),
                config.getLong("container.send-connected-peers-period", 60_000L),
                config.getString("container.healthcheck.runningContainersAtStartRegexp", ""),
                config.getInt("container.healthcheck.runningContainersCheckPeriod", 0),
                config.getString("container.filesystem", FileSystem.Type.LOCAL.name).uppercase(), // LOCAL | ZFS
                config.getString("container.host-mount-dir"),
                config.getMasterMountDir(),
                config.getString("container.zfs.pool-name", FileSystem.ZFS_POOL_NAME),
                config.getString("container.zfs.pool-init-script", CONTAINER_ZFS_INIT_SCRIPT),
                config.getBoolean("container.bind-pgdata-volume", true),
                config.getTestmode(),
                config.getLong("container.testmode.resource-limits-ram", -1),
                config.getLong("container.testmode.resource-limits-cpu", -1),
                config.getLong("container.testmode.resource-limits-storage", -1),
                initTestmodeDappsContainers(config)
            )
        }

        private fun AppConfig.getTestmode() = getBoolean("container.testmode", false)

        private fun AppConfig.getMasterMountDir(): String {
            val default = getString("container.host-mount-dir")
            return getString("container.master-mount-dir", default) // See kdoc
        }

        /*
            Example:
                cont0=331A9436,B99F6E8B,BB73F0CA
                cont1=323ECC01,A9599992,86E2043D
                cont2=EB7387FD,94F29578
                cont3=0B942264
        */
        private fun initTestmodeDappsContainers(config: AppConfig): Map<String, String> {
            val res = mutableMapOf<String, String>()

            if (config.getTestmode()) {
                listOf("cont0", "cont1", "cont2", "cont3").forEach { cont ->
                    config.getStringArray(cont).forEach { res[it] = cont }
                }
            }

            return res
        }
    }
}
