package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.PubKey

class GlobalTopicPipe(override val route: GlobalTopicsRoute, override val id: String) : IcmfPipe<GlobalTopicsRoute, String, Long> {
    override fun mightHaveNewPackets(): Boolean {
        TODO("Not yet implemented")
    }

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long>? {
        TODO("Not yet implemented")
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        TODO("Not yet implemented")
    }

    data class D1ClusterInfo(val name: String, val anchoringChain: BlockchainRid, val peers: Set<D1PeerInfo>)

    data class D1PeerInfo(val restApiUrl: String, val pubKey: PubKey) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as D1PeerInfo

            if (restApiUrl != other.restApiUrl) return false

            return true
        }

        override fun hashCode(): Int {
            return restApiUrl.hashCode()
        }
    }
}
