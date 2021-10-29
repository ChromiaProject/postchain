package net.postchain.containers

import com.spotify.docker.client.DockerClient
import net.postchain.containers.bpm.DockerClientFactory
import org.junit.Test

class ContainerTest {

    @Test
    fun test() {
        val dockerClient: DockerClient = DockerClientFactory.create()
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())

        all.forEach {
            val msg = "[NAME]: ContainerJobHandler -- DockerContainer %s: NAME, " +
                    "containerId: %s"
            println("${it.id()}\t${it.names()?.toTypedArray()?.contentToString()}")
            println(msg.format("found", it.id()))
        }
    }

}