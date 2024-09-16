package net.postchain.containers.bpm.docker

import org.mandas.docker.client.messages.Container

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
}