package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey

val cryptoSystem = Secp256K1CryptoSystem()
val privKey = PrivKey(cryptoSystem.getRandomBytes(32))
val pubKey = PubKey(secp256k1_derivePubKey(privKey.key))

fun fetchClusterInfoFromD1(clusterName: String): D1ClusterInfo =
        D1ClusterInfo(clusterName, BlockchainRid.buildRepeat(0), setOf(D1PeerInfo("", pubKey)))

fun fetchChainInfoFromD1(blockchainRid: BlockchainRid, height: Long): Set<D1PeerInfo> =
        setOf(D1PeerInfo("", pubKey))

fun lookupAllClustersInD1(): Set<String> = setOf("cluster0")

data class D1ClusterInfo(val name: String, val anchoringChain: BlockchainRid, val peers: Set<D1PeerInfo>)

data class D1PeerInfo(val restApiUrl: String, val pubKey: PubKey)
