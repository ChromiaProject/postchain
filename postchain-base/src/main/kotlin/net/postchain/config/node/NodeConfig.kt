// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.config.Config
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid

open class NodeConfig(val appConfig: AppConfig) : Config {

    open val peerInfoMap: Map<NodeRid, PeerInfo> = mapOf()
    open val nodeReplicas: Map<NodeRid, List<NodeRid>> = mapOf() // for making a node a full clone of another node
    open val blockchainReplicaNodes: Map<BlockchainRid, List<NodeRid>> = mapOf()
    open val blockchainAncestors: Map<BlockchainRid, Map<BlockchainRid, Set<NodeRid>>> = getAncestors()
    open val mustSyncUntilHeight: Map<Long, Long>? = mapOf() // chainID -> height

    private fun getAncestors(): Map<BlockchainRid, Map<BlockchainRid, Set<NodeRid>>> {
        val ancestors = appConfig.subset("blockchain_ancestors")
        val forBrids = ancestors.keys
        val result = mutableMapOf<BlockchainRid, MutableMap<BlockchainRid, MutableSet<NodeRid>>>()
        forBrids.forEach { it ->
            val list = ancestors.getList(String::class.java, it)
            val map = LinkedHashMap<BlockchainRid, MutableSet<NodeRid>>()
            list.forEach {
                val peerAndBrid = it.split(":")
                val peer = NodeRid(peerAndBrid[0].hexStringToByteArray())
                val brid = BlockchainRid.buildFromHex(peerAndBrid[1])
                map.computeIfAbsent(brid) { mutableSetOf() }.add(peer)
            }
            result[BlockchainRid.buildFromHex(it)] = map
        }
        return result
    }
}