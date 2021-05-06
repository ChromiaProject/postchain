package net.postchain.containers

import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.devtools.NameHelper

object NameService {

    fun extendedContainerName(nodePubKey: String, containerName: String): String {
        val node = nodePubKey.take(8)
        return "postchain-slavenode-$node-container$containerName"
    }

    fun databaseSchema(nodeConfig: NodeConfig, containerName: String): String {
        return "${nodeConfig.appConfig.databaseSchema}_${NameHelper.peerName(nodeConfig.pubKey)}" +
                "_$containerName"
    }

    // TODO: [POS-129]: Redesign this
    fun containerImage() = "chromaway/postchain-slavenode:3.3.1"
}