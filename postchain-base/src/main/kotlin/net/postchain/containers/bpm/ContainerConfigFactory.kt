package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Infrastructure
import net.postchain.ebft.syncmanager.common.SyncParameters
import org.mandas.docker.client.messages.ContainerConfig
import org.mandas.docker.client.messages.HostConfig
import org.mandas.docker.client.messages.LogConfig
import org.mandas.docker.client.messages.PortBinding

object ContainerConfigFactory : KLogging() {

    private const val REMOTE_DEBUG_PORT = 8000

    fun createConfig(fs: FileSystem, appConfig: AppConfig, containerNodeConfig: ContainerNodeConfig, container: PostchainContainer): ContainerConfig {
        // Container volumes
        val volumes = mutableListOf<HostConfig.Bind>()

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
        val targetVol = HostConfig.Bind.builder()
                .from(fs.hostRootOf(container.containerName).toString())
                .to(FileSystem.CONTAINER_TARGET_PATH)
                .build()
        volumes.add(targetVol)

        // pgdata volume
        if (containerNodeConfig.bindPgdataVolume) {
            val pgdataVol = HostConfig.Bind.builder()
                    .from(fs.hostPgdataOf(container.containerName).toString())
                    .to(FileSystem.CONTAINER_PGDATA_PATH)
                    .build()
            volumes.add(pgdataVol)
        }

        if (containerNodeConfig.log4jConfigurationFile != null) {
            val log4jConfigFile = HostConfig.Bind.builder()
                    .from(containerNodeConfig.log4jConfigurationFile)
                    .to(FileSystem.CONTAINER_LOG4J_PATH)
                    .build()
            volumes.add(log4jConfigFile)
        }

        if (containerNodeConfig.subnodeUser != null) {
            volumes.add(HostConfig.Bind.builder().from("/etc/passwd").to("/etc/passwd").readOnly(true).build())
            volumes.add(HostConfig.Bind.builder().from("/etc/group").to("/etc/group").readOnly(true).build())
        }

        val restApiConfig = RestApiConfig.fromAppConfig(appConfig)

        /**
         * Rest API port binding.
         * If restApiConfig.restApiPort == -1 => no communication with API => no binding needed.
         * If restApiConfig.restApiPort > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * DockerPort must be both node and container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Therefore, use random port selection
         */
        val portBindings = mutableMapOf<String, List<PortBinding>>() // { dockerPort -> hostIp:hostPort }
        // rest-api-port
        val restApiPort = "${containerNodeConfig.subnodeRestApiPort}/tcp"
        if (restApiConfig.port > -1) {
            portBindings[restApiPort] = listOf(PortBinding.randomPort(containerNodeConfig.subnodeHost))
        }
        // debug-api-port
        val debugApiPort = "${containerNodeConfig.subnodeDebugApiPort}/tcp"
        if (restApiConfig.debugPort > -1) {
            portBindings[debugApiPort] = listOf(PortBinding.randomPort(containerNodeConfig.subnodeHost))
        }
        // admin-rpc-port
        val adminRpcPort = "${containerNodeConfig.subnodeAdminRpcPort}/tcp"
        portBindings[adminRpcPort] = listOf(PortBinding.randomPort(containerNodeConfig.subnodeHost))

        if (containerNodeConfig.remoteDebugEnabled) {
            val remoteDebugPort = "$REMOTE_DEBUG_PORT/tcp"
            portBindings[remoteDebugPort] = listOf(PortBinding.randomPort(containerNodeConfig.subnodeHost))
        }

        if (containerNodeConfig.jmxBasePort > -1) {
            val calculatedJmxPort = calculateJmxPort(containerNodeConfig, container)
            val jmxPort = "$calculatedJmxPort/tcp"
            portBindings[jmxPort] = listOf(PortBinding.of(containerNodeConfig.subnodeHost, calculatedJmxPort))
        }

        /**
         * CPU:
         * $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         * Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         */

        // Host config
        val resources = container.resourceLimits
        val hostConfig = HostConfig.builder()
                .binds(*volumes.toTypedArray())
                .portBindings(portBindings)
                .publishAllPorts(false)
                .apply {
                    if (resources.hasRam()) memory(resources.ramBytes())
                }.apply {
                    if (resources.hasCpu()) {
                        cpuPeriod(resources.cpuPeriod())
                        cpuQuota(resources.cpuQuota())
                    }
                }
                .apply {
                    if (resources.hasIoRead()) {
                        blkioDeviceReadBps(listOf(
                                HostConfig.BlkioDeviceRate.builder()
                                        .path(containerNodeConfig.hostMountDevice)
                                        .rate(resources.ioReadBytes().toInt())
                                        .build()
                        ))
                    }
                }
                .apply {
                    if (resources.hasIoWrite()) {
                        blkioDeviceWriteBps(listOf(
                                HostConfig.BlkioDeviceRate.builder()
                                        .path(containerNodeConfig.hostMountDevice)
                                        .rate(resources.ioWriteBytes().toInt())
                                        .build()
                        ))
                    }
                }
                .apply {
                    if (containerNodeConfig.network != null) {
                        logger.info("Setting container network to ${containerNodeConfig.network}")
                        networkMode(containerNodeConfig.network)
                    }
                }
                .apply {
                    val dockerLogConf = containerNodeConfig.dockerLogConf
                    if (dockerLogConf != null) {
                        logger.info("Setting docker log configuration to $dockerLogConf")
                        logConfig(LogConfig.create(dockerLogConf.driver, dockerLogConf.opts))
                    }
                }
                .build()

        return ContainerConfig.builder()
                .apply {
                    containerNodeConfig.subnodeUser?.let { user(it) }
                }
                .image(getContainerImage(containerNodeConfig))
                .hostConfig(hostConfig)
                .exposedPorts(portBindings.keys)
                .env(createNodeConfigEnv(appConfig, containerNodeConfig, container))
                .labels(containerNodeConfig.labels + (POSTCHAIN_MASTER_PUBKEY to containerNodeConfig.masterPubkey))
                .build()
    }

    fun getContainerImage(config: ContainerNodeConfig): String =
            when (val expectedTag = config.imageVersionTag) {
                "" -> config.containerImage
                else -> {
                    when (val actualTag = config.containerImage.substringAfter(":", "")) {
                        "" -> config.containerImage.substringBefore(":") + ":" + expectedTag
                        else -> {
                            if (expectedTag != actualTag) {
                                logger.warn { "Container image version tag ($actualTag) is not equal to the environment image version tag ($expectedTag)" }
                            }
                            config.containerImage
                        }
                    }
                }
            }

    private fun createNodeConfigEnv(appConfig: AppConfig, containerNodeConfig: ContainerNodeConfig, container: PostchainContainer) = buildList {
        val restApiConfig = RestApiConfig.fromAppConfig(appConfig)

        add("POSTCHAIN_INFRASTRUCTURE=${Infrastructure.EbftContainerSub.get()}")

        val subnodeDatabaseUrl = appConfig.getEnvOrString("POSTCHAIN_SUBNODE_DATABASE_URL", ContainerNodeConfig.fullKey(ContainerNodeConfig.KEY_SUBNODE_DATABASE_URL))
                ?: appConfig.databaseUrl
        add("POSTCHAIN_DB_URL=${subnodeDatabaseUrl}")
        val scheme = "${appConfig.databaseSchema}_${container.containerName.directoryContainer}"
        add("POSTCHAIN_DB_SCHEMA=${scheme}")
        add("POSTCHAIN_DB_USERNAME=${appConfig.databaseUsername}")
        add("POSTCHAIN_DB_PASSWORD=${appConfig.databasePassword}")
        add("POSTCHAIN_DB_READ_CONCURRENCY=${appConfig.databaseReadConcurrency}")
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
        add("POSTCHAIN_READ_ONLY=${container.readOnly}")

        add("POSTCHAIN_EXIT_ON_FATAL_ERROR=true")
        add("POSTCHAIN_CONTAINER_ID=${container.containerName.containerIID}")
        add("POSTCHAIN_DIRECTORY_CONTAINER=${container.containerName.directoryContainer}")

        add("POSTCHAIN_PROMETHEUS_PORT=${containerNodeConfig.prometheusPort}")

        add("POSTGRES_MAX_LOCKS_PER_TRANSACTION=${containerNodeConfig.postgresMaxLocksPerTransaction}")

        add("POSTCHAIN_HOUSEKEEPING_INTERVAL_MS=${appConfig.housekeepingIntervalMs}")

        add("POSTCHAIN_TRACKED_EBFT_MESSAGE_MAX_KEEP_TIME_MS=${appConfig.trackedEbftMessageMaxKeepTimeMs}")

        add("POSTCHAIN_MASTERSUB_QUERY_TIMEOUT_MS=${containerNodeConfig.masterSubQueryTimeoutMs}")

        val javaToolOptions = createJavaToolOptions(containerNodeConfig, container)
        if (javaToolOptions.isNotEmpty()) {
            add("JAVA_TOOL_OPTIONS=${javaToolOptions.joinToString(" ")}")
        }

        containerNodeConfig.containerConfigProviders.forEach { configProviderClass ->
            val configProvider = newInstanceOf<ContainerConfigProvider>(configProviderClass)
            configProvider.getConfig(appConfig).forEach { add("${it.key}=${it.value}") }
        }
    }

    private fun createJavaToolOptions(containerNodeConfig: ContainerNodeConfig, container: PostchainContainer): List<String> {
        val options = mutableListOf<String>()
        if (containerNodeConfig.remoteDebugEnabled) {
            val suspend = if (containerNodeConfig.remoteDebugSuspend) "y" else "n"
            options.add("-agentlib:jdwp=transport=dt_socket,server=y,address=*:$REMOTE_DEBUG_PORT,suspend=$suspend")
        }

        if (containerNodeConfig.jmxBasePort > -1) {
            val jmxPort = calculateJmxPort(containerNodeConfig, container)
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
    private fun calculateJmxPort(containerNodeConfig: ContainerNodeConfig, container: PostchainContainer) =
            containerNodeConfig.jmxBasePort + container.containerName.containerIID
}
