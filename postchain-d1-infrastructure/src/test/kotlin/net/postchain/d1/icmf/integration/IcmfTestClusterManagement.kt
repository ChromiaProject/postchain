package net.postchain.d1.icmf.integration

import net.postchain.common.BlockchainRid
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.cluster.D1PeerInfo

class IcmfTestClusterManagement : ClusterManagement {
    companion object {
        private val cryptoSystem = Secp256K1CryptoSystem()
        val privKey = PrivKey(cryptoSystem.getRandomBytes(32))
        val pubKey = PubKey(secp256k1_derivePubKey(privKey.key))
    }

    private val peers = listOf(
            D1PeerInfo("http://127.0.0.1:7740/", pubKey.key),
    )

    override fun getAllClusters() = listOf("cluster1")

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long) =
            peers

    override fun getClusterInfo(clusterName: String) =
            D1ClusterInfo(clusterName, BlockchainRid.buildRepeat(0), peers)

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> =
            listOf(BlockchainRid.buildRepeat(0), BlockchainRid.buildRepeat(1))
}
