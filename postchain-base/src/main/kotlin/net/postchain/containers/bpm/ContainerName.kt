package net.postchain.containers.bpm

import net.postchain.config.node.NodeConfig

data class ContainerName(
        val directory: String,
        val fullName: String
) {

    companion object {
        fun create(nodeConfig: NodeConfig, directoryContainer: String): ContainerName {
            val nodeContainerName = "${directoryContainer}_${nodeConfig.pubKey.take(8)}"
            return ContainerName(directoryContainer, nodeContainerName)
        }
    }

    override fun toString(): String {
        return fullName
    }

}
