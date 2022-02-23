package net.postchain.containers.bpm

import com.spotify.docker.client.messages.Container

object DockerTools {

    fun checkContainerName(container: Container, containerName: String): Boolean {
        return container.names()?.contains("/$containerName") ?: false // Prefix '/'
    }

    fun containerName(container: Container): String {
        return container.names()?.get(0) ?: ""
    }

    fun shortContainerId(containerId: String?): String? {
        return containerId?.take(12)
    }
}