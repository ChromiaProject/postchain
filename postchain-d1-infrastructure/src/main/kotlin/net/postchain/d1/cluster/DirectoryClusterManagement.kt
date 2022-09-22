package net.postchain.d1.cluster

import net.postchain.core.block.BlockQueries
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toList
import net.postchain.gtv.mapper.toObject

class DirectoryClusterManagement(private val blockQueries: BlockQueries) : ClusterManagement {
    override fun getAllClusterPeerInfo(): Collection<ClusterPeerInfo> {
        return blockQueries.query("cm_get_all_clusters", gtv(mapOf())).get().toList()
    }

    override fun getClusterPeerInfo(clusterName: String): ClusterPeerInfo {
        return blockQueries.query("cm_get_cluster_info", gtv(mapOf("name" to gtv(clusterName)))).get().toObject()
    }
}
