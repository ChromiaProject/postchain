package net.postchain.d1.anchor

import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.cluster.D1PeerInfo

class AnchorTestClusterManagement : ClusterManagement {
    override fun getClusterInfo(clusterName: String): D1ClusterInfo {
        TODO("Not yet implemented")
    }

    override fun getAllClusters(): Collection<String> {
        TODO("Not yet implemented")
    }

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<D1PeerInfo> {
        return listOf(
                D1PeerInfo("http://127.0.0.1:7740/", "03a301697bdfcd704313ba48e51d567543f2a182031efd6915ddc07bbcc4e16070".hexStringToByteArray()),
                D1PeerInfo("http://127.0.0.1:7741/", "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F".hexStringToByteArray()),
                D1PeerInfo("http://127.0.0.1:7742/", "03B2EF623E7EC933C478135D1763853CBB91FC31BA909AEC1411CA253FDCC1AC94".hexStringToByteArray())
        )
    }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> {
        TODO("Not yet implemented")
    }
}