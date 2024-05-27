package net.postchain.managed

import net.postchain.managed.query.QueryRunner

interface ClusterAnchoringChainDataSource : QueryRunner{

    val nmApiVersion: Int

    fun isBlockAnchored(blockchainRid: ByteArray, blockRid: ByteArray): Boolean
}
