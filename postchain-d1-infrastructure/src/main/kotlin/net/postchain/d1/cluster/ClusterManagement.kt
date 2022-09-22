package net.postchain.d1.cluster

interface ClusterManagement {
    fun getAllClusterPeerInfo(): Collection<D1ClusterInfo>

    fun getClusterPeerInfo(clusterName: String): D1ClusterInfo
}