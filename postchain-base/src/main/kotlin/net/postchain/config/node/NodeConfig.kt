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
    open val blockchainReplicaNodes: Map<BlockchainRid, List<NodeRid>> = mapOf()
    open val locallyConfiguredBlockchainReplicaNodes: Map<BlockchainRid, List<NodeRid>> = mapOf()
    open val blockchainAncestors: Map<BlockchainRid, Map<BlockchainRid, Set<NodeRid>>> = getAncestors()
    open val mustSyncUntilHeight: Map<Long, Long>? = mapOf() // chainID -> height

    open fun getSignersInLatestConfiguration(blockchainRid: BlockchainRid, chainId: Long): List<NodeRid> = listOf()

    private fun getAncestors(): Map<BlockchainRid, Map<BlockchainRid, Set<NodeRid>>> {
        // blockchain_ancestors.<brid_X>=[<node_id_Y>:<brid_Z>]
        val allAncestors = appConfig.subset("blockchain_ancestors")
        val result = mutableMapOf<BlockchainRid, Map<BlockchainRid, Set<NodeRid>>>()
        allAncestors.keys.forEach { brid ->
            val ancestors = allAncestors.getList(String::class.java, brid)
            result[BlockchainRid.buildFromHex(brid)] = ancestors
                    .map {
                        BlockchainRid.buildFromHex(it.substringAfter(":")) to
                                NodeRid(it.substringBefore(":").hexStringToByteArray())
                    }
                    .groupBy { it.first }
                    .mapValues { e -> e.value.map { it.second }.toSet() }
        }

        return result
    }
}