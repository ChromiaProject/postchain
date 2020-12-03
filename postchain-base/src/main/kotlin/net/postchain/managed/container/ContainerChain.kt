package net.postchain.managed.container

import net.postchain.base.BlockchainRid
import net.postchain.devtools.PeerNameHelper

data class ContainerChain(
        val nodePubKey: String,
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        var containerId: String? = null
//        ,
//        var container: Container? = null
) {

    val containerName: String

    init {
        val node = PeerNameHelper.peerName(nodePubKey)
        containerName = "postchain-subnode-$node-chain$chainId-${blockchainRid.toHex().take(8)}" // TODO: [POS-129]
    }
}