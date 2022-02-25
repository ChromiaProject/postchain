package net.postchain.containers

import com.spotify.docker.client.DockerClient
import net.postchain.containers.bpm.DockerClientFactory
import org.junit.jupiter.api.Test

class ContainerTest {

    @Test
    fun test() {
        val dockerClient: DockerClient = DockerClientFactory.create()
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())

        all.forEach { c ->
            val name = c.names()?.firstOrNull() ?: ""
            println("${name}\t${c.id()}")

            // ports
            println(c.portsAsString())
            println(c.ports()?.map { "${it.publicPort()} + ${it.privatePort()} + ${it.ip()} + ${it.type()}" } ?: "")
        }
    }

}