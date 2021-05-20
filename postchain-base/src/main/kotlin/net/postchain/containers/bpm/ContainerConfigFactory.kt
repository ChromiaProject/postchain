package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.postchain.common.Utils
import net.postchain.config.node.NodeConfig
import net.postchain.containers.NameService
import java.nio.file.Path

object ContainerConfigFactory {

    fun createConfig(nodeConfig: NodeConfig, container: PostchainContainer, containerCwd: Path): ContainerConfig {
        // -v $containerCwd:/opt/chromaway/postchain/target \
        val volume = HostConfig.Bind
                .from(containerCwd.toString())
                .to("/opt/chromaway/postchain/target")
                .build()

        /**
         * Rest API port binding.
         * If nodeConfig.restApiPost == -1 => no communication with API => no binding needed.
         * If nodeConfig.restApiPost > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * DockerPort must be both node and container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Therefore use radom port selection
         */

        // TODO: Likely to be a unique port but not 100% guarantee.
        // Also not 100% sure that port is still free when connection is made
        // Should we use something even more sophisticated?
        val dockerPort = "${Utils.findFreePort()}/tcp"
        val portBindings = if (nodeConfig.restApiPort > -1) {
            mapOf(dockerPort to listOf(PortBinding.of("0.0.0.0", nodeConfig.subnodeRestApiPort)))
        } else mapOf()

        /**
        $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         */
        // Host config
        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .portBindings(portBindings)
                .publishAllPorts(true)
                .memory(container.resourceLimits?.get("ram"))
                .cpuQuota(container.resourceLimits?.get("cpu"))
//                .storageOpt(mapOf("dm.basesize" to "3G"))
//                .storageOpt(mapOf("size" to "3G"))
//                .storageOpt(mapOf("overlay2.size" to "3G"))
//                .storageOpt(container.resourceLimits?.get("storage"))
                .build()

        return ContainerConfig.builder()
                .image(NameService.containerImage())
                .hostConfig(hostConfig)
                .exposedPorts(dockerPort)
                .build()
    }

}