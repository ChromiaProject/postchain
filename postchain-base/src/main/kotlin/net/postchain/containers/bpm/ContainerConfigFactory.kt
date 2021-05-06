package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
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
         * DockerPort must be container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Unprivileged port space: [1025,2^16-1]
         */

        //Likely to be a unique port but not 100% guarantee.
        val dockerPort = "${container.containerName.hashCode() % (65535-1025) + 1025}/tcp"

        val portBindings = if (nodeConfig.restApiPort > -1) {
            mapOf(dockerPort to listOf(PortBinding.of("0.0.0.0", nodeConfig.subnodeRestApiPort)))
        } else mapOf()

        // TODO: [POS-129]: Implement random port selection
//        val portBindings = mapOf("$containerPort/tcp" to listOf(PortBinding.randomPort("0.0.0.0")))

        // Host config
        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .portBindings(portBindings)
                .publishAllPorts(true)
                .build()

        return ContainerConfig.builder()
                .image(NameService.containerImage())
                .hostConfig(hostConfig)
                .exposedPorts(dockerPort)
                .build()
    }

}