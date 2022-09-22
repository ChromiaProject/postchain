package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey

class TestClusterManagement(cs: CryptoSystem): ClusterManagement {
    private val peers = listOf(
            PeerApi("http://127.0.0.1:7740/", secp256k1_derivePubKey(cs.getRandomBytes(32))),
            PeerApi("http://127.0.0.1:7741/", secp256k1_derivePubKey(cs.getRandomBytes(32))),
            PeerApi("http://127.0.0.1:7742/", secp256k1_derivePubKey(cs.getRandomBytes(32))),
    )

    override fun getAllClusterPeerInfo(): Collection<ClusterPeerInfo> {
        return listOf(
                ClusterPeerInfo("c1", BlockchainRid.buildRepeat(1), setOf(peers[0], peers[1])),
                ClusterPeerInfo("c2", BlockchainRid.buildRepeat(2), setOf(peers[1], peers[2]))
        )
    }

    override fun getClusterPeerInfo(clusterName: String): ClusterPeerInfo {
        return ClusterPeerInfo(clusterName, BlockchainRid.buildRepeat(1), setOf(peers[0], peers[1]))
    }
}
