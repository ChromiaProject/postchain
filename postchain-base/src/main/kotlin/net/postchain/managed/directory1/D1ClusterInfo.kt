package net.postchain.managed.directory1

import net.postchain.common.BlockchainRid
import net.postchain.gtv.mapper.Name

data class D1ClusterInfo(
        @Name("name") val name: String,
        @Name("anchoring_chain") val anchoringChain: BlockchainRid,
        @Name("peers") val peers: Set<D1PeerInfo>
)
