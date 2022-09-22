package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid

interface ClusterManagement {
    fun getClusterInfo(clusterName: String): D1ClusterInfo
    fun getAllClusters(): Collection<String>
    fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<D1PeerInfo>
}
