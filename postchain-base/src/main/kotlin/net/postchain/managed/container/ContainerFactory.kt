package net.postchain.managed.container

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import java.nio.file.Path

object ContainerFactory {

    fun createContainer(dockerClient: DockerClient, containerCwd: Path, chain: ContainerChain): String? {
        // -v $containerCwd:/opt/chromaway/postchain-subnode/rte \
        val volume = HostConfig.Bind
                .from(containerCwd.toString())
                .to("/opt/chromaway/postchain-subnode/rte")
                .build()

        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .build()

        val containerConfig = ContainerConfig.builder()
                .image("chromaway/postchain-subnode:3.2.1")
                .hostConfig(hostConfig)
                .build()

        val containerCreation = dockerClient.createContainer(containerConfig, chain.containerName)
        return containerCreation.id()
    }

}