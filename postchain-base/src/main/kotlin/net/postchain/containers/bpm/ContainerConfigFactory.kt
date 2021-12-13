package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.ContainerInfo
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.postchain.config.node.NodeConfig
import net.postchain.containers.NameService
import net.postchain.containers.infra.ContainerResourceType

object ContainerConfigFactory {

    fun createConfig(nodeConfig: NodeConfig, container: PostchainContainer, containerDir: String): ContainerConfig {
        // -v $containerCwd:/opt/chromaway/postchain/target \
        val volume = HostConfig.Bind
                .from(containerDir)
                .to("/opt/chromaway/postchain/target")
                .build()

        /**
         * Rest API port binding.
         * If nodeConfig.restApiPost == -1 => no communication with API => no binding needed.
         * If nodeConfig.restApiPost > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * DockerPort must be both node and container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Therefore use random port selection
         */
        // Likely to be a unique port but not 100% guarantee.
        // Also, not 100% sure that port is still free when connection is made
        val dockerPort = "${nodeConfig.subnodeRestApiPort}/tcp"
        val portBindings = if (nodeConfig.restApiPort > -1) {
            mapOf(dockerPort to listOf(PortBinding.of("0.0.0.0", container.restApiPort)))
        } else mapOf()

        /**
         * CPU:
         * $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         * Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         *
         * STORAGE: Limited Storage quota with bind mount:
         * You can’t use Docker CLI commands to directly manage bind mounts.
         * $ docker run -d -it --name devtest \
         * --mount type=bind,source="$(pwd)"/target,target=/app ubuntu /bin/bash
         */

        // Host config
        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .portBindings(portBindings)
                .publishAllPorts(true)
                .memory(container.resourceLimits?.get(ContainerResourceType.RAM))
                .cpuQuota(container.resourceLimits?.get(ContainerResourceType.CPU))
//                .storageOpt(mapOf("dm.basesize" to "3G"))
//                .storageOpt(mapOf("size" to "3G"))
//                .storageOpt(mapOf("overlay2.size" to "3G"))
//                .storageOpt(container.resourceLimits?.get("storage"))
                .build()

        return ContainerConfig.builder()
                .image(NameService.containerImage(nodeConfig))
                .hostConfig(hostConfig)
                .exposedPorts(dockerPort)
                .build()
    }

    fun getHostPort(containerInfo: ContainerInfo, containerPort: Int): Int? {
        val bindings = containerInfo.hostConfig()?.portBindings()?.get("${containerPort}/tcp")
        return bindings?.firstOrNull()?.hostPort()?.toInt()
    }

}