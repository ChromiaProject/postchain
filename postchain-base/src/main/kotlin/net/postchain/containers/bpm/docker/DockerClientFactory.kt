package net.postchain.containers.bpm.docker

import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder

object DockerClientFactory {

    fun create(): DockerClient = JerseyDockerClientBuilder().fromEnv().build()

}