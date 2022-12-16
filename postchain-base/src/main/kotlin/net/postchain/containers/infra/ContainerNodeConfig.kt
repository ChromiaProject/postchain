package net.postchain.containers.infra

import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.server.config.PostchainServerConfig
import org.apache.commons.configuration2.Configuration

/**
 * Container chains configuration
 *
 * @param masterHost Used by subnode to connect to master for inter-node communication
 * @param subnodeHost Used by master for restAPI communication with subnode
 * @param healthcheckRunningContainersCheckPeriod In number of blocks of chain0, set 0 to disable a check
 */
data class ContainerNodeConfig(
        val masterPubkey: String,
        val containerImage: String,
        val masterHost: String,
        val masterPort: Int,
        val masterRestApiPort: Int,
        val network: String?,
        val subnodeHost: String,
        val subnodeRestApiPort: Int,
        val subnodeAdminRpcPort: Int,
        val sendMasterConnectedPeersPeriod: Long,
        val healthcheckRunningContainersCheckPeriod: Int,
        // Container FileSystem
        val containerFilesystem: String,

        /**
         * A dir in host filesystem where container volume will be created.
         * [net.postchain.containers.bpm.ContainerConfigFactory] uses it to create a docker volume for subnode container.
         *
         * If master node is launched natively or by means of fabric8 maven plugin or Testcontainers Lib or CI/CD,
         * [masterMountDir] has to be equal to [hostMountDir] ([masterMountDir] can be omitted in config)
         *
         * If master node is launched inside a container, i.e. in case of DinD,
         * [masterMountDir] might not be equal to [hostMountDir] (see subnode Dockerfile for details)
         */
        val hostMountDir: String,

        /**
         * A path to dir where container volume is placed in the master (container) filesystem.
         * [net.postchain.containers.bpm.ContainerInitializer] uses it to create container node config file,
         * blockchains dir, etc.
         *
         * If master node is launched natively or by means of fabric8 maven plugin or Testcontainers Lib or CI/CD,
         * [masterMountDir] has to be equal to [hostMountDir] ([masterMountDir] can be omitted in config)
         *
         * If master node is launched inside a container, i.e. in case of DinD,
         * [masterMountDir] might not be equal to [hostMountDir] (see subnode Dockerfile for details)
         */
        val masterMountDir: String,
        val zfsPoolName: String,
        val zfsPoolInitScript: String,
        val bindPgdataVolume: Boolean,
        val testmode: Boolean,
        val testmodeResourceLimitsCPU: Long,
        val testmodeResourceLimitsRAM: Long,
        val testmodeResourceLimitsSTORAGE: Long,
        val testmodeDappsContainers: Map<String, String>,
) : Config {
    companion object {
        const val DEFAULT_CONTAINER_ZFS_INIT_SCRIPT = "container-zfs-init-script.sh"

        const val KEY_CONTAINER_PREFIX = "container"
        const val KEY_DOCKER_IMAGE = "docker-image"
        const val KEY_MASTER_HOST = "master-host"
        const val KEY_MASTER_PORT = "master-port"
        const val KEY_MASTER_REST_API_PORT = "master-rest-api-port"
        const val KEY_NETWORK = "network"
        const val KEY_SUBNODE_HOST = "subnode-host"
        const val KEY_SUBNODE_REST_API_PORT = "rest-api-port"
        const val KEY_SUBNODE_ADMIN_RPC_PORT = "admin-rpc-port"
        const val KEY_SEND_MASTER_CONNECTED_PEERS_PERIOD = "send-master-connected-peers-period"
        const val KEY_HEALTHCHECK_RUNNING_CONTAINERS_CHECK_PERIOD = "healthcheck.running-containers-check-period"
        const val KEY_SUBNODE_DATABASE_URL = "subnode-database-url"
        const val KEY_SUBNODE_FILESYSTEM = "filesystem"
        const val KEY_HOST_MOUNT_DIR = "host-mount-dir"
        const val KEY_MASTER_MOUNT_DIR = "master-mount-dir"
        const val KEY_ZFS_POOL_NAME = "zfs.pool-name"
        const val KEY_ZFS_POOL_INIT_SCRIPT = "zfs.pool-init-script"
        const val KEY_BIND_PGDATA_VOLUME = "bind-pgdata-volume"
        const val KEY_TESTMODE_RESOURCE_LIMITS_CPU = "testmode.resource-limits-cpu"
        const val KEY_TESTMODE_RESOURCE_LIMITS_RAM = "testmode.resource-limits-ram"
        const val KEY_TESTMODE_RESOURCE_LIMITS_STORAGE = "testmode.resource-limits-storage"

        fun fullKey(subKey: String) = "$KEY_CONTAINER_PREFIX.${subKey}"

        @JvmStatic
        fun fromAppConfig(config: AppConfig): ContainerNodeConfig {
            return with(config.subset(KEY_CONTAINER_PREFIX)) {
                ContainerNodeConfig(
                        config.pubKey,
                        getString(KEY_DOCKER_IMAGE, "chromaway/postchain-subnode:latest"),
                        getString(KEY_MASTER_HOST, "localhost"),
                        getInt(KEY_MASTER_PORT, 9860),
                        getMasterRestApiPort(config),
                        getString(KEY_NETWORK),
                        getString(KEY_SUBNODE_HOST, "localhost"),
                        getInt(KEY_SUBNODE_REST_API_PORT, RestApiConfig.DEFAULT_REST_API_PORT),
                        getInt(KEY_SUBNODE_ADMIN_RPC_PORT, PostchainServerConfig.DEFAULT_RPC_SERVER_PORT),
                        getLong(KEY_SEND_MASTER_CONNECTED_PEERS_PERIOD, 60_000L),
                        getInt(KEY_HEALTHCHECK_RUNNING_CONTAINERS_CHECK_PERIOD, 0),
                        getString(KEY_SUBNODE_FILESYSTEM, FileSystem.Type.LOCAL.name).uppercase(), // LOCAL | ZFS
                        getString(KEY_HOST_MOUNT_DIR),
                        getMasterMountDir(),
                        getString(KEY_ZFS_POOL_NAME, FileSystem.ZFS_POOL_NAME),
                        getString(KEY_ZFS_POOL_INIT_SCRIPT, DEFAULT_CONTAINER_ZFS_INIT_SCRIPT),
                        getBoolean(KEY_BIND_PGDATA_VOLUME, true),
                        getTestmode(),
                        getLong(KEY_TESTMODE_RESOURCE_LIMITS_CPU, -1),
                        getLong(KEY_TESTMODE_RESOURCE_LIMITS_RAM, -1),
                        getLong(KEY_TESTMODE_RESOURCE_LIMITS_STORAGE, -1),
                        initTestmodeDappsContainers()
                )
            }
        }

        private fun Configuration.getMasterRestApiPort(config: AppConfig): Int {
            return if (containsKey(KEY_MASTER_REST_API_PORT)) {
                getInt(KEY_MASTER_REST_API_PORT)
            } else {
                RestApiConfig.fromAppConfig(config).port
            }
        }

        private fun Configuration.getTestmode() = getBoolean("testmode", false)

        private fun Configuration.getMasterMountDir(): String {
            return getString(KEY_MASTER_MOUNT_DIR, getString(KEY_HOST_MOUNT_DIR)) // See kdoc
        }

        /*
            Example:
                container.cont0=331A9436,B99F6E8B,BB73F0CA
                container.cont1=323ECC01,A9599992,86E2043D
                container.cont2=EB7387FD,94F29578
                container.cont3=0B942264
        */
        private fun Configuration.initTestmodeDappsContainers(): Map<String, String> {
            return if (getTestmode()) {
                listOf("cont0", "cont1", "cont2", "cont3")
                        .flatMap { cont -> getStringArray(cont).map { brid -> brid to cont } }
                        .toMap()
            } else {
                mapOf()
            }
        }
    }
}
