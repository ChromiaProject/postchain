// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.core.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.core.NodeRid

interface PeerInfoDataSource {
    fun getPeerInfos(): Array<PeerInfo>
    fun getNodeReplicaMap(): Map<NodeRid, List<NodeRid>>
    fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<NodeRid>>
    fun getSyncUntilHeight(): Map<BlockchainRid, Long>
}