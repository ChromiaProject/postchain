package net.postchain.managed.directory1

import net.postchain.common.BlockchainRid

data class D1ClusterInfo(
        val name: String,
        val anchoringChain: BlockchainRid,
        val peers: Set<D1PeerInfo>
)
