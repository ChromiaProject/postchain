package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey

fun fetchClusterInfoFromD1(clusterName: String): D1ClusterInfo = D1ClusterInfo(clusterName, BlockchainRid.buildRepeat(0), setOf(D1PeerInfo("", PubKey(ByteArray(33))))) // TODO Implement

fun fetchChainInfoFromD1(blockchainRid: BlockchainRid, height: Long): Set<D1PeerInfo> = setOf(D1PeerInfo("", PubKey(ByteArray(33)))) // TODO Implement

data class D1ClusterInfo(val name: String, val anchoringChain: BlockchainRid, val peers: Set<D1PeerInfo>)

data class D1PeerInfo(val restApiUrl: String, val pubKey: PubKey)
