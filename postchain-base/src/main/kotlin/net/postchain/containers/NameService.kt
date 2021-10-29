package net.postchain.containers

import net.postchain.config.node.NodeConfig

object NameService {

    fun databaseSchema(nodeConfig: NodeConfig, nodeContainerName: String): String {
        return "${nodeConfig.appConfig.databaseSchema}_$nodeContainerName"
    }

    fun containerImage(nodeConfig: NodeConfig): String {
        return nodeConfig.containerImage
    }
}