package net.postchain.containers.bpm

import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.BlkioRateDevice
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.LogConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Infrastructure
import net.postchain.ebft.syncmanager.common.SyncParameters

object ContainerConfigFactory : KLogging() {

    private const val REMOTE_DEBUG_PORT = 8000

    fun setConfig(createContainerCmd: CreateContainerCmd, fs: FileSystem, appConfig: AppConfig,
                  containerNodeConfig: ContainerNodeConfig, containerName: ContainerName,
                  resourceLimits: ContainerResourceLimits, readOnly: Boolean) {

        val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
        val volumes = createVolumes(fs, containerName, containerNodeConfig)
        val portBindings = createPortBindings(restApiConfig, containerNodeConfig, containerName)
        val hostConfig = createHostConfig(volumes, portBindings, resourceLimits, containerNodeConfig)

        createContainerCmd
                .apply {
                    containerNodeConfig.subnodeUser?.let { withUser(it) }
                }
                .withName(containerName.dockerContainer)
                .withHostConfig(hostConfig)
                .withExposedPorts(portBindings.map { it.exposedPort })
                .withEnv(createNodeConfigEnv(appConfig, containerNodeConfig, containerName, readOnly))
                .withLabels(containerNodeConfig.labels + (POSTCHAIN_MASTER_PUBKEY to containerNodeConfig.masterPubkey))
    }

    private fun createHostConfig(volumes: MutableList<Bind>, portBindings: MutableList<PortBinding>, resourceLimits: ContainerResourceLimits, containerNodeConfig: ContainerNodeConfig): HostConfig? {

        /**
         * CPU:
         * $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         * Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         */

        return HostConfig.newHostConfig()
                .withBinds(volumes)
                .withPortBindings(portBindings)
                .withPublishAllPorts(false)
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(listOf("no-new-privileges:true"))
                .apply {
                    if (resourceLimits.hasRam()) withMemory(resourceLimits.ramBytes())
                }
                .apply {
                    if (resourceLimits.hasCpu()) {
                        withCpuPeriod(resourceLimits.cpuPeriod())
                        withCpuQuota(resourceLimits.cpuQuota())
                    }
                }
                .apply {
                    if (resourceLimits.hasIoRead()) {
                        withBlkioDeviceReadBps(listOf(
                                BlkioRateDevice()
                                        .withPath(containerNodeConfig.hostMountDevice)
                                        .withRate(resourceLimits.ioReadBytes())
                        ))
                    }
                }
                .apply {
                    if (resourceLimits.hasIoWrite()) {
                        withBlkioDeviceWriteBps(listOf(
                                BlkioRateDevice()
                                        .withPath(containerNodeConfig.hostMountDevice)
                                        .withRate(resourceLimits.ioWriteBytes())
                        ))
                    }
                }
                .apply {
                    if (containerNodeConfig.network != null) {
                        logger.info("Setting container network to ${containerNodeConfig.network}")
                        withNetworkMode(containerNodeConfig.network)
                    }
                }
                .apply {
                    val dockerLogConf = containerNodeConfig.dockerLogConf
                    if (dockerLogConf != null) {
                        logger.info("Setting docker log configuration to $dockerLogConf")
                        withLogConfig(LogConfig(dockerLogConf.driver, dockerLogConf.opts))
                    }
                }
    }

    private fun createPortBindings(restApiConfig: RestApiConfig, containerNodeConfig: ContainerNodeConfig, containerName: ContainerName): MutableList<PortBinding> {
        /**
         * Rest API port binding.
         * If restApiConfig.restApiPort == -1 => no communication with API => no binding needed.
         * If restApiConfig.restApiPort > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * DockerPort must be both node and container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Therefore, use random port selection
         */
        val portBindings = mutableListOf<PortBinding>()
        // rest-api-port
        if (restApiConfig.port > -1) {
            portBindings.add(PortBinding(Ports.Binding.bindIp(containerNodeConfig.subnodeHost), ExposedPort(containerNodeConfig.subnodeRestApiPort)))
        }
        // debug-api-port
        if (restApiConfig.debugPort > -1) {
            portBindings.add(PortBinding(Ports.Binding.bindIp(containerNodeConfig.subnodeHost), ExposedPort(containerNodeConfig.subnodeDebugApiPort)))
        }
        // admin-rpc-port
        portBindings.add(PortBinding(Ports.Binding.bindIp(containerNodeConfig.subnodeHost), ExposedPort(containerNodeConfig.subnodeAdminRpcPort)))

        if (containerNodeConfig.remoteDebugEnabled) {
            portBindings.add(PortBinding(Ports.Binding.bindIp(containerNodeConfig.subnodeHost), ExposedPort(REMOTE_DEBUG_PORT)))
        }

        if (containerNodeConfig.jmxBasePort > -1) {
            val calculatedJmxPort = calculateJmxPort(containerNodeConfig, containerName)
            portBindings.add(PortBinding(Ports.Binding.bindIp(containerNodeConfig.subnodeHost), ExposedPort(calculatedJmxPort)))
        }
        return portBindings
    }

    private fun createVolumes(fs: FileSystem, containerName: ContainerName, containerNodeConfig: ContainerNodeConfig): MutableList<Bind> {

        val volumes = mutableListOf<Bind>()

        /**
         *  Bindings:
         *      host                            container
         *      ====                            =========
         *
         *      local fs:
         *      ---------
         *      container-dir                   /opt/chromaway/postchain/target
         *
         *      zfs fs:
         *      -------
         *      /psvol/container-dir            /opt/chromaway/postchain/target
         *      /psvol/container-dir/pgdata     /var/lib/postgresql/data
         */

        // target volume
        volumes.add(Bind(fs.hostRootOf(containerName).toString(), Volume(FileSystem.CONTAINER_TARGET_PATH)))

        // pgdata volume
        if (containerNodeConfig.bindPgdataVolume) {
            volumes.add(Bind(fs.hostPgdataOf(containerName).toString(), Volume(FileSystem.CONTAINER_PGDATA_PATH)))
        }

        if (containerNodeConfig.log4jConfigurationFile != null) {
            volumes.add(Bind(containerNodeConfig.log4jConfigurationFile, Volume(FileSystem.CONTAINER_LOG4J_PATH), AccessMode.ro))
        }

        if (containerNodeConfig.subnodeUser != null) {
            volumes.add(Bind("/etc/passwd", Volume("/etc/passwd"), AccessMode.ro))
            volumes.add(Bind("/etc/group", Volume("/etc/group"), AccessMode.ro))
        }

        return volumes
    }

    private fun createNodeConfigEnv(appConfig: AppConfig, containerNodeConfig: ContainerNodeConfig, containerName: ContainerName, readOnly: Boolean) = buildList {
        val restApiConfig = RestApiConfig.fromAppConfig(appConfig)

        add("POSTCHAIN_INFRASTRUCTURE=${Infrastructure.EbftContainerSub.get()}")

        val subnodeDatabaseUrl = appConfig.getEnvOrString("POSTCHAIN_SUBNODE_DATABASE_URL", ContainerNodeConfig.fullKey(ContainerNodeConfig.KEY_SUBNODE_DATABASE_URL))
                ?: appConfig.databaseUrl
        add("POSTCHAIN_DB_URL=${subnodeDatabaseUrl}")
        val scheme = "${appConfig.databaseSchema}_${containerName.directoryContainer}"
        add("POSTCHAIN_DB_SCHEMA=${scheme}")
        add("POSTCHAIN_DB_USERNAME=${appConfig.databaseUsername}")
        add("POSTCHAIN_DB_PASSWORD=${appConfig.databasePassword}")
        add("POSTCHAIN_DB_BLOCK_BUILDER_READ_CONCURRENCY=${appConfig.databaseBlockBuilderReadConcurrency}")
        add("POSTCHAIN_DB_SHARED_READ_CONCURRENCY=${appConfig.databaseSharedReadConcurrency}")
        add("POSTCHAIN_DB_BLOCK_BUILDER_WRITE_CONCURRENCY=${appConfig.databaseBlockBuilderWriteConcurrency}")
        add("POSTCHAIN_DB_SHARED_WRITE_CONCURRENCY=${appConfig.databaseSharedWriteConcurrency}")
        add("POSTCHAIN_DB_BLOCK_BUILDER_MAX_WAIT_WRITE=${appConfig.databaseBlockBuilderMaxWaitWrite}")
        add("POSTCHAIN_DB_SHARED_MAX_WAIT_WRITE=${appConfig.databaseSharedMaxWaitWrite}")
        add("POSTCHAIN_CRYPTO_SYSTEM=${appConfig.cryptoSystemClass}")
        // Do not add privKey. It is supplied through initialization via gRPC
        add("POSTCHAIN_PUBKEY=${appConfig.pubKey}")
        add("POSTCHAIN_PORT=${appConfig.port}")
        add("POSTCHAIN_FASTSYNC_EXIT_DELAY=${SyncParameters.fromAppConfig(appConfig).exitDelay}")

        /**
         * If restApiPort > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * If -1, no API communication => subnodeRestApiPort=-1
         */
        val restApiPort = if (restApiConfig.port > -1) {
            containerNodeConfig.subnodeRestApiPort
        } else {
            -1
        }
        add("POSTCHAIN_API_PORT=${restApiPort}")

        val debugApiPort = if (restApiConfig.debugPort > -1) {
            containerNodeConfig.subnodeDebugApiPort
        } else {
            -1
        }
        add("POSTCHAIN_DEBUG_PORT=${debugApiPort}")

        add("POSTCHAIN_MASTER_HOST=${containerNodeConfig.masterHost}")
        add("POSTCHAIN_MASTER_PORT=${containerNodeConfig.masterPort}")
        add("POSTCHAIN_HOST_MOUNT_DIR=${containerNodeConfig.hostMountDir}")
        add("POSTCHAIN_HOST_MOUNT_DEVICE=${containerNodeConfig.hostMountDevice}")
        add("POSTCHAIN_SUBNODE_DOCKER_IMAGE=${containerNodeConfig.containerImage}")
        add("POSTCHAIN_SUBNODE_HOST=${containerNodeConfig.subnodeHost}")
        add("POSTCHAIN_SUBNODE_NETWORK=${containerNodeConfig.network}")
        add("POSTCHAIN_READ_ONLY=$readOnly")

        add("POSTCHAIN_EXIT_ON_FATAL_ERROR=true")
        add("POSTCHAIN_CONTAINER_ID=${containerName.containerIID}")
        add("POSTCHAIN_DIRECTORY_CONTAINER=${containerName.directoryContainer}")

        add("POSTCHAIN_PROMETHEUS_PORT=${containerNodeConfig.prometheusPort}")

        add("POSTGRES_MAX_LOCKS_PER_TRANSACTION=${containerNodeConfig.postgresMaxLocksPerTransaction}")

        add("POSTCHAIN_HOUSEKEEPING_INTERVAL_MS=${appConfig.housekeepingIntervalMs}")

        add("POSTCHAIN_TRACKED_EBFT_MESSAGE_MAX_KEEP_TIME_MS=${appConfig.trackedEbftMessageMaxKeepTimeMs}")

        add("POSTCHAIN_MASTERSUB_QUERY_TIMEOUT_MS=${containerNodeConfig.masterSubQueryTimeoutMs}")

        val javaToolOptions = createJavaToolOptions(containerNodeConfig, containerName)
        if (javaToolOptions.isNotEmpty()) {
            add("JAVA_TOOL_OPTIONS=${javaToolOptions.joinToString(" ")}")
        }

        containerNodeConfig.containerConfigProviders.forEach { configProviderClass ->
            val configProvider = newInstanceOf<ContainerConfigProvider>(configProviderClass)
            configProvider.getConfig(appConfig).forEach { add("${it.key}=${it.value}") }
        }

        // Custom extensions can inject config this way
        appConfig.getKeys("extension").forEach {
            add("POSTCHAIN_${it.replace(".", "_").uppercase()}=${appConfig.getProperty(it)}")
        }
        addAll(
                System.getenv()
                        .filterKeys { it.startsWith("POSTCHAIN_EXTENSION_") }
                        .map { (key, value) -> "$key=$value" }
        )
    }

    private fun createJavaToolOptions(containerNodeConfig: ContainerNodeConfig, containerName: ContainerName): List<String> {
        val options = mutableListOf<String>()
        if (containerNodeConfig.remoteDebugEnabled) {
            val suspend = if (containerNodeConfig.remoteDebugSuspend) "y" else "n"
            options.add("-agentlib:jdwp=transport=dt_socket,server=y,address=*:$REMOTE_DEBUG_PORT,suspend=$suspend")
        }

        if (containerNodeConfig.jmxBasePort > -1) {
            val jmxPort = calculateJmxPort(containerNodeConfig, containerName)
            options.add("-Dcom.sun.management.jmxremote")
            options.add("-Dcom.sun.management.jmxremote.authenticate=false")
            options.add("-Dcom.sun.management.jmxremote.ssl=false")
            options.add("-Dcom.sun.management.jmxremote.port=$jmxPort")
            options.add("-Dcom.sun.management.jmxremote.rmi.port=$jmxPort")
            options.add("-Djava.rmi.server.hostname=localhost")
        }

        return options
    }

    /**
     * Unfortunately JMX RMI connections will only work if internal port is mapped to the same host port.
     * To make ports unique per subnode we use the scheme JMX_BASE_PORT + CONTAINER_IID.
     * Should be a good enough workaround for debugging purposes.
     */
    private fun calculateJmxPort(containerNodeConfig: ContainerNodeConfig, containerName: ContainerName) =
            containerNodeConfig.jmxBasePort + containerName.containerIID
}
