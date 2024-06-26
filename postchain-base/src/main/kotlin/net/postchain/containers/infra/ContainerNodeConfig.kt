package net.postchain.containers.infra

import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.common.config.Config
import net.postchain.common.config.getEnvOrBooleanProperty
import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrListProperty
import net.postchain.common.config.getEnvOrLongProperty
import net.postchain.common.config.getEnvOrStringProperty
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerConfigFactory
import net.postchain.containers.bpm.fs.FileSystem

/**
 * Container chains configuration
 *
 * @param masterHost Used by subnode to connect to master for inter-node communication
 * @param subnodeHost Used by master for restAPI communication with subnode
 * @param healthcheckRunningContainersCheckPeriod In milliseconds, set 0 to disable health check
 */
data class ContainerNodeConfig(
        val masterPubkey: String,
        val containerImage: String,
        val masterHost: String,
        val masterPort: Int,
        val network: String?,
        val subnodeHost: String,
        val subnodeRestApiPort: Int,
        val subnodeDebugApiPort: Int,
        val subnodeAdminRpcPort: Int,
        val subnodeUser: String?,
        val sendMasterConnectedPeersPeriod: Long,
        val healthcheckRunningContainersCheckPeriod: Long,
        val idleTimeoutMs: Long,
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
         * The device where [hostMountDir] is located on the host.
         */
        val hostMountDevice: String,

        /**
         * A path to dir where container volume is placed in the master (container) filesystem.
         * [net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager.initContainerWorkingDir] uses it to
         * initialize it.
         *
         * If master node is launched natively or by means of fabric8 maven plugin or Testcontainers Lib or CI/CD,
         * [masterMountDir] has to be equal to [hostMountDir] ([masterMountDir] can be omitted in config)
         *
         * If master node is launched inside a container, i.e. in case of DinD,
         * [masterMountDir] might not be equal to [hostMountDir] (see subnode Dockerfile for details)
         */
        val masterMountDir: String,
        val zfsPoolName: String,
        val zfsPoolInitScript: String?,
        val bindPgdataVolume: Boolean,
        val dockerLogConf: DockerLogConfig?,
        val containerIID: Int,
        val directoryContainer: String,
        val remoteDebugEnabled: Boolean,
        val remoteDebugSuspend: Boolean,
        val prometheusPort: Int,
        val jmxBasePort: Int,
        val minSpaceQuotaBufferMB: Int,
        val labels: Map<String, String>,
        val log4jConfigurationFile: String?,
        val postgresMaxLocksPerTransaction: Int,
        val containerConfigProviders: List<String>,
        val adminClientTimeoutMs: Int,
        val masterSubQueryTimeoutMs: Long,
        val imageVersionTag: String // hidden param, not for configuring manually
) : Config {
    val subnodePorts = listOf(subnodeRestApiPort, subnodeDebugApiPort, subnodeAdminRpcPort)

    companion object {
        const val DEFAULT_CONTAINER_ZFS_INIT_SCRIPT = "container-zfs-init-script.sh"

        const val KEY_CONTAINER_PREFIX = "container"
        const val KEY_DOCKER_IMAGE = "docker-image"
        const val KEY_DOCKER_LOG_DRIVER = "docker-log-driver"
        const val KEY_DOCKER_LOG_OPTS = "docker-log-opts"
        const val KEY_MASTER_HOST = "master-host"
        const val KEY_MASTER_PORT = "master-port"
        const val KEY_NETWORK = "network"
        const val KEY_SUBNODE_HOST = "subnode-host"
        const val KEY_SUBNODE_REST_API_PORT = "rest-api-port"
        const val KEY_SUBNODE_DEBUG_API_PORT = "debug-api-port"
        const val KEY_SUBNODE_ADMIN_RPC_PORT = "admin-rpc-port"
        const val KEY_SUBNODE_USER = "subnode-user"
        const val KEY_SEND_MASTER_CONNECTED_PEERS_PERIOD = "send-master-connected-peers-period"
        const val KEY_HEALTHCHECK_RUNNING_CONTAINERS_CHECK_PERIOD = "healthcheck.running-containers-check-period"
        const val KEY_SUBNODE_IDLE_TIMEOUT_MS = "idle-timeout-ms"
        const val KEY_SUBNODE_DATABASE_URL = "subnode-database-url"
        const val KEY_SUBNODE_FILESYSTEM = "filesystem"
        const val KEY_HOST_MOUNT_DIR = "host-mount-dir"
        const val KEY_HOST_MOUNT_DEVICE = "host-mount-device"
        const val KEY_MASTER_MOUNT_DIR = "master-mount-dir"
        const val KEY_ZFS_POOL_NAME = "zfs.pool-name"
        const val KEY_ZFS_POOL_INIT_SCRIPT = "zfs.pool-init-script"
        const val KEY_BIND_PGDATA_VOLUME = "bind-pgdata-volume"
        const val KEY_REMOTE_DEBUG_ENABLED = "remote-debug-enabled"
        const val KEY_REMOTE_DEBUG_SUSPEND = "remote-debug-suspend"
        const val KEY_PROMETHEUS_PORT = "metrics.prometheus.port"
        const val KEY_JMX_BASE_PORT = "jmx-base-port"
        const val KEY_MIN_SPACE_QUOTA_BUFFER_MB = "min-space-quota-buffer-mb"
        const val KEY_LABEL = "label"
        const val KEY_LOG4J_CONFIGURATION_FILE = "log4j-configuration-file"
        const val KEY_POSTGRES_MAX_LOCKS_PER_TRANSACTION = "postgres_max_locks_per_transaction"
        const val KEY_CONFIG_PROVIDERS = "config-providers"
        const val KEY_ADMIN_CLIENT_TIMEOUT_MS = "admin-client-timeout-ms"
        const val KEY_MASTERSUB_QUERY_TIMEOUT_MS = "mastersub-query-timeout-ms"
        const val KEY_IMAGE_VERSION_TAG = "IMAGE_VERSION_TAG" // hidden param, not for configuring manually

        fun fullKey(subKey: String) = "$KEY_CONTAINER_PREFIX.${subKey}"

        @JvmStatic
        fun fromAppConfig(config: AppConfig): ContainerNodeConfig {
            return with(config.subset(KEY_CONTAINER_PREFIX)) {
                val hostMountDir = getEnvOrStringProperty("POSTCHAIN_HOST_MOUNT_DIR", KEY_HOST_MOUNT_DIR)
                        ?: throw UserMistake("$KEY_CONTAINER_PREFIX.$KEY_HOST_MOUNT_DIR must be specified")
                val hostMountDevice = getEnvOrStringProperty("POSTCHAIN_HOST_MOUNT_DEVICE", KEY_HOST_MOUNT_DEVICE)
                        ?: throw UserMistake("$KEY_CONTAINER_PREFIX.$KEY_HOST_MOUNT_DEVICE must be specified")
                val subnodeImage = getEnvOrStringProperty("POSTCHAIN_SUBNODE_DOCKER_IMAGE", KEY_DOCKER_IMAGE)
                        ?: throw UserMistake("$KEY_CONTAINER_PREFIX.$KEY_DOCKER_IMAGE must be specified")
                val masterHost = getEnvOrStringProperty("POSTCHAIN_MASTER_HOST", KEY_MASTER_HOST)
                        ?: throw UserMistake("$KEY_CONTAINER_PREFIX.$KEY_MASTER_HOST must be specified")
                val subnodeHost = getEnvOrStringProperty("POSTCHAIN_SUBNODE_HOST", KEY_SUBNODE_HOST)
                        ?: throw UserMistake("$KEY_CONTAINER_PREFIX.$KEY_SUBNODE_HOST must be specified")

                val subnodeUser = getEnvOrStringProperty("POSTCHAIN_SUBNODE_USER", KEY_SUBNODE_USER) ?: try {
                    val unixSystem = com.sun.security.auth.module.UnixSystem()
                    if (unixSystem.uid == 0L) null else "${unixSystem.uid}:${unixSystem.gid}"
                } catch (e: Exception) {
                    ContainerConfigFactory.logger.warn("Unable to fetch current user id: $e")
                    null
                } catch (le: LinkageError) {
                    ContainerConfigFactory.logger.warn("Fetching current user id is unsupported: $le")
                    null
                }

                val logDriver = getEnvOrStringProperty("POSTCHAIN_DOCKER_LOG_DRIVER", KEY_DOCKER_LOG_DRIVER, "")
                val logOpts = getEnvOrStringProperty("POSTCHAIN_DOCKER_LOG_OPTS", KEY_DOCKER_LOG_OPTS, "")
                val logConf = DockerLogConfig.fromStrings(logDriver, logOpts)
                val labels = buildMap {
                    getKeys(KEY_LABEL).forEach { put(it.substring(KEY_LABEL.length + 1), getString(it)) }
                }

                ContainerNodeConfig(
                        config.pubKey,
                        subnodeImage,
                        masterHost,
                        getEnvOrIntProperty("POSTCHAIN_MASTER_PORT", KEY_MASTER_PORT, 9860),
                        getEnvOrStringProperty("POSTCHAIN_SUBNODE_NETWORK", KEY_NETWORK),
                        subnodeHost,
                        subnodeRestApiPort = getEnvOrIntProperty("POSTCHAIN_SUBNODE_REST_API_PORT", KEY_SUBNODE_REST_API_PORT, RestApiConfig.DEFAULT_REST_API_PORT),
                        subnodeDebugApiPort = getEnvOrIntProperty("POSTCHAIN_SUBNODE_DEBUG_API_PORT", KEY_SUBNODE_DEBUG_API_PORT, RestApiConfig.DEFAULT_DEBUG_API_PORT),
                        subnodeAdminRpcPort = getEnvOrIntProperty("POSTCHAIN_SUBNODE_ADMIN_RPC_PORT", KEY_SUBNODE_ADMIN_RPC_PORT, 50051),
                        subnodeUser,
                        getEnvOrLongProperty("POSTCHAIN_SEND_MASTER_CONNECTED_PEERS_PERIOD", KEY_SEND_MASTER_CONNECTED_PEERS_PERIOD, 60_000L),
                        getEnvOrLongProperty("POSTCHAIN_HEALTHCHECK_RUNNING_CONTAINERS_CHECK_PERIOD", KEY_HEALTHCHECK_RUNNING_CONTAINERS_CHECK_PERIOD, 60_000),
                        getEnvOrLongProperty("POSTCHAIN_SUBNODE_IDLE_TIMEOUT_MS", KEY_SUBNODE_IDLE_TIMEOUT_MS, 5 * 60_000),
                        getEnvOrStringProperty("POSTCHAIN_SUBNODE_FILESYSTEM", KEY_SUBNODE_FILESYSTEM, FileSystem.Type.LOCAL.name).uppercase(), // LOCAL | ZFS
                        hostMountDir,
                        hostMountDevice,
                        getEnvOrStringProperty("POSTCHAIN_MASTER_MOUNT_DIR", KEY_MASTER_MOUNT_DIR, hostMountDir),
                        getEnvOrStringProperty("POSTCHAIN_ZFS_POOL_NAME", KEY_ZFS_POOL_NAME, FileSystem.ZFS_POOL_NAME),
                        getEnvOrStringProperty("POSTCHAIN_ZFS_POOL_INIT_SCRIPT", KEY_ZFS_POOL_INIT_SCRIPT),
                        getEnvOrBooleanProperty("POSTCHAIN_BIND_PGDATA_VOLUME", KEY_BIND_PGDATA_VOLUME, true),
                        logConf,
                        System.getenv("POSTCHAIN_CONTAINER_ID")?.toInt() ?: -1,
                        System.getenv("POSTCHAIN_DIRECTORY_CONTAINER")?.toString() ?: "",
                        getEnvOrBooleanProperty("POSTCHAIN_SUBNODE_REMOTE_DEBUG_ENABLED", KEY_REMOTE_DEBUG_ENABLED, false),
                        getEnvOrBooleanProperty("POSTCHAIN_SUBNODE_REMOTE_DEBUG_SUSPEND", KEY_REMOTE_DEBUG_SUSPEND, false),
                        getEnvOrIntProperty("POSTCHAIN_SUBNODE_PROMETHEUS_PORT", KEY_PROMETHEUS_PORT, -1),
                        getEnvOrIntProperty("POSTCHAIN_SUBNODE_JMX_BASE_PORT", KEY_JMX_BASE_PORT, -1),
                        getEnvOrIntProperty("POSTCHAIN_SUBNODE_MIN_SPACE_QUOTA_BUFFER_MB", KEY_MIN_SPACE_QUOTA_BUFFER_MB, 100),
                        labels,
                        getEnvOrStringProperty("POSTCHAIN_SUBNODE_LOG4J_CONFIGURATION_FILE", KEY_LOG4J_CONFIGURATION_FILE),
                        getEnvOrIntProperty("POSTCHAIN_SUBNODE_POSTGRES_MAX_LOCKS_PER_TRANSACTION", KEY_POSTGRES_MAX_LOCKS_PER_TRANSACTION, 1024),
                        getEnvOrListProperty("POSTCHAIN_SUBNODE_CONFIG_PROVIDERS", KEY_CONFIG_PROVIDERS, emptyList()),
                        getEnvOrIntProperty("POSTCHAIN_SUBNODE_ADMIN_CLIENT_TIMEOUT_MS", KEY_ADMIN_CLIENT_TIMEOUT_MS, 60_000),
                        getEnvOrLongProperty("POSTCHAIN_MASTERSUB_QUERY_TIMEOUT_MS", KEY_MASTERSUB_QUERY_TIMEOUT_MS, 10_000),
                        System.getenv(KEY_IMAGE_VERSION_TAG) ?: ""
                )
            }
        }
    }
}
