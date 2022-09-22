package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey

class TestClusterManagement(cs: CryptoSystem): ClusterManagement {
    private val peers = listOf(
            D1PeerInfo("http://127.0.0.1:7740/", secp256k1_derivePubKey(cs.getRandomBytes(32))),
            D1PeerInfo("http://127.0.0.1:7741/", secp256k1_derivePubKey(cs.getRandomBytes(32))),
            D1PeerInfo("http://127.0.0.1:7742/", secp256k1_derivePubKey(cs.getRandomBytes(32))),
    )

    override fun getAllClusterPeerInfo(): Collection<D1ClusterInfo> {
        return listOf(
                D1ClusterInfo("c1", BlockchainRid.buildRepeat(1), setOf(peers[0], peers[1])),
                D1ClusterInfo("c2", BlockchainRid.buildRepeat(2), setOf(peers[1], peers[2]))
        )
    }

    override fun getClusterPeerInfo(clusterName: String): D1ClusterInfo {
        return D1ClusterInfo(clusterName, BlockchainRid.buildRepeat(1), setOf(peers[0], peers[1]))
    }
}
