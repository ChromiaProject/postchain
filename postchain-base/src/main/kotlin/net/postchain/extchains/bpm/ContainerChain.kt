package net.postchain.extchains.bpm

import com.spotify.docker.client.messages.Container
import net.postchain.base.BlockchainRid
import net.postchain.devtools.PeerNameHelper

data class ContainerChain(
        val nodePubKey: String,
        val chainId: Long,
        var blockchainRid: BlockchainRid? = null,
        var containerId: String? = null,
        var container: Container? = null
) {

    val containerName: String

    init {
        val node = PeerNameHelper.peerName(nodePubKey, "-")
        containerName = "postchain-subnode-$node-chain$chainId"
    }
}