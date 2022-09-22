package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.gtv.mapper.Name

data class ClusterPeerInfo(
        @Name("name") val name: String,
        @Name("anchoring_chain") val anchoringChain: BlockchainRid,
        @Name("peers") val peers: Set<PeerApi>
)
