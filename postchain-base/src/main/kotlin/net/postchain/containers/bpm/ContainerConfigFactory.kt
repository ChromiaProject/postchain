package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.postchain.config.node.NodeConfig
import net.postchain.containers.NameService
import net.postchain.containers.NameService.containerRestAPIPort
import java.nio.file.Path

object ContainerConfigFactory {

    fun createConfig(nodeConfig: NodeConfig, container: PostchainContainer, containerCwd: Path): ContainerConfig {
        // -v $containerCwd:/opt/chromaway/postchain/target \
        val volume = HostConfig.Bind
                .from(containerCwd.toString())
                .to("/opt/chromaway/postchain/target")
                .build()

        // Rest API port binding
        val containerPort = "${nodeConfig.restApiPort}/tcp"
        val hostPort = containerRestAPIPort(nodeConfig, container.containerName)

        val portBindings = mapOf(containerPort to listOf(PortBinding.of("0.0.0.0", hostPort)))
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
                .exposedPorts(containerPort)
                .build()
    }

}