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
     * Tries to find host ports pair by [containerPorts] pair for given [dockerContainerId].
     */
    fun DockerClient.findHostPorts(dockerContainerId: String, containerPorts: Pair<Int, Int>): Pair<Int, Int> {
        val info = this.inspectContainer(dockerContainerId)
        val hostPort1 = info.hostPortFor(containerPorts.first)
                ?: throw ProgrammerMistake("Container has no mapped port")
        val hostPort2 = info.hostPortFor(containerPorts.second)
                ?: throw ProgrammerMistake("Container has no mapped port")
        return hostPort1 to hostPort2
    }

    private fun ContainerInfo.hostPortFor(port: Int) = networkSettings()?.ports()?.get("${port}/tcp")
            ?.firstOrNull()?.hostPort()?.toInt()
}