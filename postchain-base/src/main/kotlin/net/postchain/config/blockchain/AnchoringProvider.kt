package net.postchain.config.blockchain

import net.postchain.managed.ClusterAnchoringChainDataSource

interface AnchoringProvider {
    fun isAnchored(blockchainRid: ByteArray, blockRid: ByteArray): Boolean
}

class AnchoringProviderImpl(val cacDataSource: ClusterAnchoringChainDataSource) : AnchoringProvider {
    override fun isAnchored(blockchainRid: ByteArray, blockRid: ByteArray): Boolean {
        return cacDataSource.isBlockAnchored(blockchainRid, blockRid)
    }
}

class DummyAnchoringProvider : AnchoringProvider {
    override fun isAnchored(blockchainRid: ByteArray, blockRid: ByteArray): Boolean {
        return true
    }
}
