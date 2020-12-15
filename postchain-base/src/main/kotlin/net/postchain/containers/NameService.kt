package net.postchain.containers

import net.postchain.base.BlockchainRid

object NameService {

    fun containerName(nodePubKey: String, chainId: Long, blockchainRid: BlockchainRid): String {
        val node = nodePubKey.take(8)
        return "postchain-subnode-$node-chain$chainId-${blockchainRid.toHex().take(8)}"
    }

    fun databaseSchema(chainId: Long) = "subnode_$chainId"

    // TODO: [POS-129]: Redesign this
    fun containerImage() = "chromaway/postchain-subnode:3.2.1"
}