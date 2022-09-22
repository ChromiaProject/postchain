package net.postchain.d1.cluster

interface ClusterManagement {
    fun getAllClusterPeerInfo(): Collection<ClusterPeerInfo>

    fun getClusterPeerInfo(clusterName: String): ClusterPeerInfo
}