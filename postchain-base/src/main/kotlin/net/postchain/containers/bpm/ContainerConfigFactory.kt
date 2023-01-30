package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Infrastructure
import net.postchain.ebft.syncmanager.common.SyncParameters
import org.mandas.docker.client.messages.ContainerConfig
import org.mandas.docker.client.messages.HostConfig
import org.mandas.docker.client.messages.PortBinding

object ContainerConfigFactory : KLogging() {

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

        val userSpec = try {
            val unixSystem = com.sun.security.auth.module.UnixSystem()
            if (unixSystem.uid == 0L) null else "${unixSystem.uid}:${unixSystem.gid}"
        } catch (e: Exception) {
            logger.warn("Unable to fetch current user id: $e")
            null
        } catch (le: LinkageError) {
            logger.warn("Fetching current user id is unsupported: $le")
            null
        }

        if (userSpec != null) {
            volumes.add(HostConfig.Bind.builder().from("/etc/passwd").to("/etc/passwd").readOnly(true).build())
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
        val containerPorts = container.containerPorts
        // rest-api-port
        val restApiPort = "${containerPorts.restApiPort}/tcp"
        if (restApiConfig.port > -1) {
            portBindings[restApiPort] = listOf(PortBinding.of(containerNodeConfig.subnodeHost, containerPorts.hostRestApiPort))
        }
        // admin-rpc-port
        val adminRpcPort = "${containerPorts.adminRpcPort}/tcp"
        portBindings[adminRpcPort] = listOf(PortBinding.of(containerNodeConfig.subnodeHost, containerPorts.hostAdminRpcPort))

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
                    if (containerNodeConfig.network != null) {
                        logger.info("Setting container network to ${containerNodeConfig.network}")
                        networkMode(containerNodeConfig.network)
                    }
                }
                .build()

        return ContainerConfig.builder()
                .apply {
                    if (userSpec != null)
                        user(userSpec)
                }
                .image(containerNodeConfig.containerImage)
                .hostConfig(hostConfig)
                .exposedPorts(portBindings.keys)
                .env(createNodeConfigEnv(appConfig, containerNodeConfig, container))
                .labels(mapOf(POSTCHAIN_MASTER_PUBKEY to containerNodeConfig.masterPubkey))
                .build()
    }

    private fun createNodeConfigEnv(appConfig: AppConfig, containerNodeConfig: ContainerNodeConfig, container: PostchainContainer) = buildList {
        val restApiConfig = RestApiConfig.fromAppConfig(appConfig)

        add("POSTCHAIN_DEBUG=${restApiConfig.debug}")

        add("POSTCHAIN_NODE_CONFIG_PROVIDER=${NodeConfigProviders.Manual.name.lowercase()}")
        add("POSTCHAIN_INFRASTRUCTURE=${Infrastructure.EbftContainerSub.get()}")

        val subnodeDatabaseUrl = appConfig.getEnvOrString("POSTCHAIN_SUBNODE_DATABASE_URL", ContainerNodeConfig.fullKey(ContainerNodeConfig.KEY_SUBNODE_DATABASE_URL))
                ?: appConfig.databaseUrl
        add("POSTCHAIN_DB_URL=${subnodeDatabaseUrl}")
        val scheme = "${appConfig.databaseSchema}_${container.containerName.directoryContainer}"
        add("POSTCHAIN_DB_SCHEMA=${scheme}")
        add("POSTCHAIN_DB_USERNAME=${appConfig.databaseUsername}")
        add("POSTCHAIN_DB_PASSWORD=${appConfig.databasePassword}")
        add("POSTCHAIN_DB_READ_CONCURRENCY=${appConfig.databaseReadConcurrency}")
        add("POSTCHAIN_CRYPTO_SYSTEM=${appConfig.cryptoSystemClass}")
        add("POSTCHAIN_PRIVKEY=${appConfig.privKey}")
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

        add("POSTCHAIN_MASTER_HOST=${containerNodeConfig.masterHost}")
        add("POSTCHAIN_MASTER_PORT=${containerNodeConfig.masterPort}")
        add("POSTCHAIN_HOST_MOUNT_DIR=${containerNodeConfig.hostMountDir}")
        add("POSTCHAIN_SUBNODE_DOCKER_IMAGE=${containerNodeConfig.containerImage}")
        add("POSTCHAIN_SUBNODE_HOST=${containerNodeConfig.subnodeHost}")
        add("POSTCHAIN_SUBNODE_NETWORK=${containerNodeConfig.network}")
    }
}
