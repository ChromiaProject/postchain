package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.ContainerInfo
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.postchain.config.node.NodeConfig

object ContainerConfigFactory {

    fun createConfig(fs: FileSystem, nodeConfig: NodeConfig, container: PostchainContainer): ContainerConfig {
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
                .to(fs.containerTargetPath())
                .build()
        volumes.add(targetVol)

        // pgdata volume
        if (nodeConfig.containerBindPgdataVolume) {
            val pgdataVol = HostConfig.Bind
                    .from(fs.hostPgdataOf(container.containerName).toString())
                    .to(fs.containerPgdataPath())
                    .build()
            volumes.add(pgdataVol)
        }

        /**
         * Rest API port binding.
         * If nodeConfig.restApiPost == -1 => no communication with API => no binding needed.
         * If nodeConfig.restApiPost > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * DockerPort must be both node and container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Therefore, use random port selection
         */
        val dockerPort = "${nodeConfig.subnodeRestApiPort}/tcp"
        val portBindings = if (nodeConfig.restApiPort > -1) {
            mapOf(dockerPort to listOf(PortBinding.of("0.0.0.0", container.restApiPort)))
        } else mapOf()

        /**
         * CPU:
         * $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         * Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         */

        // Host config
        val hostConfig = HostConfig.builder()
                .appendBinds(*volumes.toTypedArray())
                .portBindings(portBindings)
                .publishAllPorts(true)
                .apply {
                    if (container.resourceLimits.ram > 0) memory(container.resourceLimits.ram)
                }.apply {
                    if (container.resourceLimits.cpu > 0) cpuQuota(container.resourceLimits.cpu)
                }
                .build()

        return ContainerConfig.builder()
                .image(nodeConfig.containerImage)
                .hostConfig(hostConfig)
                .exposedPorts(dockerPort)
                .build()
    }

    fun getHostPort(containerInfo: ContainerInfo, containerPort: Int): Int? {
        val bindings = containerInfo.hostConfig()?.portBindings()?.get("${containerPort}/tcp")
        return bindings?.firstOrNull()?.hostPort()?.toInt()
    }

}