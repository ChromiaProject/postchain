package net.postchain.devtools.mminfra

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.NodeRid
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.utils.ChainUtil
import net.postchain.managed.ManagedNodeDataSource

typealias Key = Pair<BlockchainRid, Long>

open class MockManagedNodeDataSource(val nodeIndex: Int) : ManagedNodeDataSource {
    // Brid -> (height -> Pair(BlockchainConfiguration, binaryBlockchainConfig)
    val bridToConfs: MutableMap<BlockchainRid, MutableMap<Long, Pair<BlockchainConfiguration, ByteArray>>> = mutableMapOf()
    private val chainToNodeSet: MutableMap<BlockchainRid, ManagedModeTest.NodeSet> = mutableMapOf()
    private val extraReplicas = mutableMapOf<BlockchainRid, MutableSet<NodeRid>>()

    override fun getPeerListVersion(): Long {
        return 1L
    }

    override fun computeBlockchainList(): List<ByteArray> {
        return chainToNodeSet.filterValues { it.contains(nodeIndex) }.keys.map { it.data }
    }

    //Does not return the real blockchain configuration byteArray
    override fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray? {
        val l = bridToConfs[BlockchainRid(blockchainRidRaw)] ?: return null
        var conf: ByteArray? = null
        for (entry in l) {
            if (entry.key <= height) {
                conf = toByteArray(Key(BlockchainRid(blockchainRidRaw), entry.key))
            } else {
                return conf
            }
        }
        return conf
    }

    override fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long? {
        val l = bridToConfs[BlockchainRid(blockchainRidRaw)] ?: return null
        for (h in l.keys) {
            if (h > height) {
                return h
            }
        }
        return null
    }

    override fun getPeerInfos(): Array<PeerInfo> {
        return emptyArray()
    }

    override fun getSyncUntilHeight(): Map<BlockchainRid, Long> {
        return emptyMap()
    }

    override fun getNodeReplicaMap(): Map<NodeRid, List<NodeRid>> {
        return mapOf()
    }

    override fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<NodeRid>> {
        val result = mutableMapOf<BlockchainRid, List<NodeRid>>()
        chainToNodeSet.keys.union(extraReplicas.keys).forEach {
            val replicaSet = chainToNodeSet[it]?.replicas ?: emptySet()
            val replicas = replicaSet.map { NodeRid(KeyPairHelper.pubKey(it)) }.toMutableSet()
            replicas.addAll(extraReplicas[it] ?: emptySet())
            result.put(it, replicas.toList())
        }
        return result
    }

    fun addExtraReplica(brid: BlockchainRid, replica: NodeRid) {
        extraReplicas.computeIfAbsent(brid) { mutableSetOf<NodeRid>() }.add(replica)
    }

    private fun key(brid: BlockchainRid, height: Long): Key {
        return Pair(brid, height)
    }

    private fun toByteArray(key: Key): ByteArray {
        var heightHex = key.second.toString(8)
        if (heightHex.length % 2 == 1) {
            heightHex = "0" + heightHex
        }
        return (key.first.toHex() + heightHex).hexStringToByteArray()
    }

    private fun toKey(bytes: ByteArray): Key {
        val rid = BlockchainRid(bytes.copyOf(32))
        val height = bytes.copyOfRange(32, bytes.size).toHex().toLong(8)
        return Key(rid, height)
    }

    fun getConf(bytes: ByteArray): BlockchainConfiguration? {
        val key = toKey(bytes)

        return bridToConfs[key.first]?.get(key.second)?.first
    }

    fun addConf(rid: BlockchainRid, height: Long, conf: BlockchainConfiguration, nodeSet: ManagedModeTest.NodeSet, rawBcConf: ByteArray) {
        val confs = bridToConfs.computeIfAbsent(rid) { sortedMapOf() }
        if (confs.put(height, Pair(conf, rawBcConf)) != null) {
            throw IllegalArgumentException("Setting blockchain configuraion for height that already has a configuration")
        } else {
            awaitDebug("### NEW BC CONFIG for chain: ${nodeSet.chain} (bc rid: ${rid.toShortHex()}) at height: $height")
        }
        chainToNodeSet.put(ChainUtil.ridOf(nodeSet.chain), nodeSet)
    }

    /**
     * This is to force a node to become totally unaware of a certain blockchain.
     */
    fun delBlockchain(rid: BlockchainRid) {
        bridToConfs.remove(rid)
        extraReplicas.remove(rid)
        chainToNodeSet.remove(rid)
    }
}
