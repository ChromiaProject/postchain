package net.postchain.containers.bpm.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.transport.DockerHttpClient
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import java.time.Duration


object DockerClientFactory {

    private const val DEFAULT_UNIX_ENDPOINT: String = "unix:///var/run/docker.sock"
    private const val DEFAULT_ADDRESS: String = "localhost"
    private const val DEFAULT_PORT: Int = 2375

    fun create(): DockerClient {

        val endPointFromEnv: String = System.getenv("POSTCHAIN_DOCKER_HOST") ?: System.getenv("DOCKER_HOST") ?: defaultDockerEndpoint()

        val config: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(endPointFromEnv)
                .build()

        val httpClient: DockerHttpClient = ZerodepDockerHttpClient.Builder()
                .dockerHost(config.dockerHost)
                .connectionTimeout(Duration.ofSeconds(20))
                .responseTimeout(Duration.ofSeconds(45))
                .build()

        return DockerClientImpl.getInstance(config, httpClient)
    }

    private fun defaultDockerEndpoint(): String {
        val os: String = System.getProperty("os.name").lowercase()
        return if (os.equals("linux", ignoreCase = true) || os.contains("mac")) {
            DEFAULT_UNIX_ENDPOINT
        } else {
            "${DEFAULT_ADDRESS}:$DEFAULT_PORT"
        }
    }
}