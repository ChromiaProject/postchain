package net.postchain.containers

import net.postchain.config.node.NodeConfig

object NameService {

    fun extendedContainerName(nodePubKey: String, containerName: String): String {
        val node = nodePubKey.take(8)
        return "postchain-slavenode-$node-container$containerName"
    }

    fun databaseSchema(nodeConfig: NodeConfig, nodeContainerName: String): String {
        return "${nodeConfig.appConfig.databaseSchema}_$nodeContainerName"
    }

    fun containerImage(nodeConfig: NodeConfig): String {
        return nodeConfig.containerImage
    }
}