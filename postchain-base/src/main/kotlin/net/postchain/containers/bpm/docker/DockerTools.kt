package net.postchain.containers.bpm.docker

import net.postchain.common.exception.ProgrammerMistake
import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.messages.Container
import org.mandas.docker.client.messages.ContainerInfo

object DockerTools {

    fun Container.hasName(containerName: String): Boolean {
        return names()?.contains("/$containerName") ?: false // Prefix '/'
    }

    fun containerName(container: Container): String {
        return container.names()?.get(0) ?: ""
    }

    fun shortContainerId(containerId: String?): String? {
        return containerId?.take(12)
    }

    /**
     * Tries to find host port mappings for [containerPorts] given [dockerContainerId].
     */
    fun DockerClient.findHostPorts(dockerContainerId: String, containerPorts: List<Int>): Map<Int, Int> {
        val info = this.inspectContainer(dockerContainerId)
        return containerPorts.associateWith {
            info.hostPortFor(it) ?: throw ProgrammerMistake("Container has no mapped port")
        }
    }

    private fun ContainerInfo.hostPortFor(port: Int) = networkSettings()?.ports()?.get("${port}/tcp")
            ?.firstOrNull()?.hostPort()?.toInt()
}