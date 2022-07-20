package net.postchain.containers.bpm

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import net.postchain.common.Utils.findFreePort
import net.postchain.common.Utils.findFreePorts

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
            fun hostPortFor(port: Int) = info.hostConfig()?.portBindings()?.get("${port}/tcp")
                    ?.firstOrNull()?.hostPort()?.toInt()

            fun Int?.orFindFree() = this ?: findFreePort()

            val hostPort1 = hostPortFor(containerPorts.first)
            val hostPort2 = hostPortFor(containerPorts.second)

            when {
                hostPort1 == null && hostPort2 == null -> findFreePorts()
                else -> hostPort1.orFindFree() to hostPort2.orFindFree()
            }

        } else {
            findFreePorts()
        }
    }
}