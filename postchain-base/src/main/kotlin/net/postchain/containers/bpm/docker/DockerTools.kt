package net.postchain.containers.bpm.docker

import net.postchain.common.NetworkUtils.findFreePort
import net.postchain.common.NetworkUtils.findFreePorts
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
     * Tries to find host ports pair by [containerPorts] pair for given [dockerContainer].
     * If [dockerContainer] is null, or it doesn't have portBinding for one or two container ports,
     * then one or two host ports will be returned available at the host.
     */
    fun DockerClient.findHostPorts(dockerContainer: Container?, containerPorts: Pair<Int, Int>): Pair<Int, Int> {
        return if (dockerContainer != null) {
            val info = this.inspectContainer(dockerContainer.id())
            val hostPort1 = info.hostPortFor(containerPorts.first)
            val hostPort2 = info.hostPortFor(containerPorts.second)
            when {
                hostPort1 == null && hostPort2 == null -> findFreePorts()
                else -> getOrFindFree(hostPort1) to getOrFindFree(hostPort2)
            }

        } else {
            findFreePorts()
        }
    }

    private fun ContainerInfo.hostPortFor(port: Int) = hostConfig()?.portBindings()?.get("${port}/tcp")
            ?.firstOrNull()?.hostPort()?.toInt()

    private fun getOrFindFree(port: Int?) = port ?: findFreePort()
}