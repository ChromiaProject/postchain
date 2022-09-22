package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toList
import net.postchain.gtv.mapper.toObject

class DirectoryClusterManagement(private val blockQueries: BlockQueries) : ClusterManagement {

    override fun getClusterPeerInfo(clusterName: String): D1ClusterInfo {
        return blockQueries.query("cm_get_cluster_info", gtv(mapOf("name" to gtv(clusterName)))).get().toObject()
    }

    override fun getAllClusters(): Collection<String> {
        return blockQueries.query("cm_get_cluster_names", gtv(mapOf())).get().asArray().map { it.asString() }
    }

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<D1PeerInfo> {
        return blockQueries.query("cm_get_peer_info", gtv(mapOf(
                "name" to gtv(blockchainRid),
                "height" to gtv(height)
        ))).get().toList()
    }
}
