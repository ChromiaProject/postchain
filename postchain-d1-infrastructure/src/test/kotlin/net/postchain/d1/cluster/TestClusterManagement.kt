package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey

class TestClusterManagement : ClusterManagement {
    private val cryptoSystem = Secp256K1CryptoSystem()

    private val peers = listOf(
            D1PeerInfo("http://127.0.0.1:7740/", secp256k1_derivePubKey(cryptoSystem.getRandomBytes(32))),
            D1PeerInfo("http://127.0.0.1:7741/", secp256k1_derivePubKey(cryptoSystem.getRandomBytes(32))),
            D1PeerInfo("http://127.0.0.1:7742/", secp256k1_derivePubKey(cryptoSystem.getRandomBytes(32)))
    )

    override fun getAllClusters() = listOf("cluster1")

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long) =
            when (blockchainRid) {
                BlockchainRid.buildRepeat(1) -> setOf(peers[0], peers[1])
                BlockchainRid.buildRepeat(2) -> setOf(peers[1], peers[2])
                else -> throw IllegalArgumentException("Brid not in any test cluster")
            }

    override fun getClusterInfo(clusterName: String) =
            when (clusterName) {
                "c1" -> D1ClusterInfo(clusterName, BlockchainRid.buildRepeat(1), setOf(peers[0], peers[1]))
                "c2" -> D1ClusterInfo(clusterName, BlockchainRid.buildRepeat(2), setOf(peers[1], peers[2]))
                else -> throw IllegalArgumentException("Cluster not found")
            }
}
