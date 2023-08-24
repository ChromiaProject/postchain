// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid

interface PeerInfoDataSource {
    fun getPeerInfos(): Array<PeerInfo>
    fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<NodeRid>>
}