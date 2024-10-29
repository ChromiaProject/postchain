package net.postchain.containers.bpm.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.ListContainersCmd
import com.github.dockerjava.api.model.Container
import net.postchain.containers.bpm.POSTCHAIN_MASTER_PUBKEY
import net.postchain.containers.infra.ContainerNodeConfig


object DockerTools {

    fun Container.hasName(containerName: String): Boolean {
        return names?.contains("/$containerName") ?: false // Prefix '/'
    }

    fun containerName(container: Container): String {
        return container.names?.get(0) ?: ""
    }

    fun shortContainerId(containerId: String?): String? {
        return containerId?.take(12)
    }

    fun DockerClient.listSubContainersCmd(containerNodeConfig: ContainerNodeConfig): ListContainersCmd {
        return listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(mapOf(POSTCHAIN_MASTER_PUBKEY to containerNodeConfig.masterPubkey))
    }
}