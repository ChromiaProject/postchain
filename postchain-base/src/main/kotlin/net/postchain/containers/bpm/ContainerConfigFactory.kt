package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig

object ContainerConfigFactory {

    fun createConfig(fs: FileSystem, restApiConfig: RestApiConfig, containerNodeConfig: ContainerNodeConfig, container: PostchainContainer): ContainerConfig {
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
        val targetVol = HostConfig.Bind
                .from(fs.hostRootOf(container.containerName).toString())
                .to(FileSystem.CONTAINER_TARGET_PATH)
                .build()
        volumes.add(targetVol)

        // pgdata volume
        if (containerNodeConfig.bindPgdataVolume) {
            val pgdataVol = HostConfig.Bind
                    .from(fs.hostPgdataOf(container.containerName).toString())
                    .to(FileSystem.CONTAINER_PGDATA_PATH)
                    .build()
            volumes.add(pgdataVol)
        }

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
            portBindings[restApiPort] = listOf(PortBinding.of("0.0.0.0", containerPorts.hostRestApiPort))
        }
        // admin-rpc-port
        val adminRpcPort = "${containerPorts.adminRpcPort}/tcp"
        portBindings[adminRpcPort] = listOf(PortBinding.of("0.0.0.0", containerPorts.hostAdminRpcPort))

        /**
         * CPU:
         * $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         * Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         */

        // Host config
        val resources = container.resourceLimits
        val hostConfig = HostConfig.builder()
                .appendBinds(*volumes.toTypedArray())
                .portBindings(portBindings)
                .publishAllPorts(true)
                .apply {
                    if (resources.hasRam()) memory(resources.ramBytes())
                }.apply {
                    if (resources.hasCpu()) {
                        cpuPeriod(resources.cpuPeriod())
                        cpuQuota(resources.cpuQuota())
                    }
                }
                .build()

        return ContainerConfig.builder()
                .image(containerNodeConfig.containerImage)
                .hostConfig(hostConfig)
                .exposedPorts(portBindings.keys)
                .env("POSTCHAIN_DEBUG=${restApiConfig.debug}")
                .build()
    }

}