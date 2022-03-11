package net.postchain.containers.bpm

import net.postchain.config.node.NodeConfig

data class ContainerName(
        val name: String,
        val directory: String
) {

    companion object {

        fun create(nodeConfig: NodeConfig, directory: String): ContainerName {
            val name = "n${nodeConfig.pubKey.take(8)}_${directory}"
            return ContainerName(name, directory)
        }

        fun createGroupName(groupName: String) = ContainerName(groupName, "")

    }

    fun isGroup(groupName: String): Boolean = name == groupName

    override fun toString(): String {
        return name
    }
}
