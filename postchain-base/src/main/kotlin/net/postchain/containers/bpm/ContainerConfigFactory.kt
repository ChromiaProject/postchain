package net.postchain.containers.bpm

import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import net.postchain.containers.NameService
import java.nio.file.Path

object ContainerConfigFactory {

    fun createConfig(containerCwd: Path): ContainerConfig {
        // -v $containerCwd:/opt/chromaway/postchain/target \
        val volume = HostConfig.Bind
                .from(containerCwd.toString())
                .to("/opt/chromaway/postchain/target")
                .build()

        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .build()

        return ContainerConfig.builder()
                .image(NameService.containerImage())
                .hostConfig(hostConfig)
                .build()
    }

}