package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toList
import net.postchain.gtv.mapper.toObject

class DirectoryClusterManagement(private val query: (String, Gtv) -> Gtv) : ClusterManagement {

    override fun getClusterInfo(clusterName: String): D1ClusterInfo {
        return query("cm_get_cluster_info", gtv(mapOf("name" to gtv(clusterName)))).toObject()
    }

    override fun getAllClusters(): Collection<String> {
        return query("cm_get_cluster_names", gtv(mapOf())).asArray().map { it.asString() }
    }

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<D1PeerInfo> {
        return query("cm_get_peer_info", gtv(mapOf(
                "name" to gtv(blockchainRid),
                "height" to gtv(height)
        ))).toList()
    }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> {
        return query("get_cluster_blockchains", gtv(mapOf(
                "name" to gtv(clusterName)
        ))).asArray().map { BlockchainRid(it.asByteArray()) }
    }
}
