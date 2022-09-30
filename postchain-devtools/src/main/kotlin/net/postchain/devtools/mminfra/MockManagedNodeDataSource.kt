package net.postchain.devtools.mminfra

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.NodeRid
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.Gtv
import net.postchain.managed.ManagedNodeDataSource
import nl.komponents.kovenant.Promise

open class MockManagedNodeDataSource(val nodeIndex: Int) : ManagedNodeDataSource {
    // Brid -> (height -> Pair(BlockchainConfiguration, binaryBlockchainConfig)
    val bridToConfigs: MutableMap<BlockchainRid, MutableMap<Long, Pair<BlockchainConfiguration, ByteArray>>> = mutableMapOf()
    private val chainToNodeSet: MutableMap<BlockchainRid, ManagedModeTest.NodeSet> = mutableMapOf()
    private val extraReplicas = mutableMapOf<BlockchainRid, MutableSet<NodeRid>>()

    override fun getPeerListVersion(): Long {
        return 1L
    }

    override fun computeBlockchainList(): List<ByteArray> {
        return chainToNodeSet.filterValues { it.contains(nodeIndex) }.keys.map { it.data }
    }

    override fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray? {
        val configs = bridToConfigs[BlockchainRid(blockchainRidRaw)] ?: return null
        var config: ByteArray? = null
        for ((h, c) in configs) {
            if (h <= height) {
                config = c.second
            } else {
                return config
            }
        }
        return config
    }

    override fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long? {
        val configs = bridToConfigs[BlockchainRid(blockchainRidRaw)] ?: return null
        for (h in configs.keys) {
            if (h > height) {
                return h
            }
        }
        return null
    }

    override fun query(name: String, args: Gtv): Gtv {
        TODO("Not yet implemented")
    }

    override fun queryAsync(name: String, args: Gtv): Promise<Gtv, Exception> {
        TODO("Not yet implemented")
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
            result[it] = replicas.toList()
        }
        return result
    }

    fun addExtraReplica(brid: BlockchainRid, replica: NodeRid) {
        extraReplicas.computeIfAbsent(brid) { mutableSetOf<NodeRid>() }.add(replica)
    }

    fun getBuiltConfiguration(chainId: Long, rawConfigurationData: ByteArray): BlockchainConfiguration {
        val brid = ChainUtil.ridOf(chainId)
        val configs = bridToConfigs[brid]!!
        return configs.values
                .first { cfgToRaw ->
                    cfgToRaw.second.contentEquals(rawConfigurationData)
                }.first
    }

    fun addConf(rid: BlockchainRid, height: Long, conf: BlockchainConfiguration, nodeSet: ManagedModeTest.NodeSet, rawBcConf: ByteArray) {
        val configs = bridToConfigs.computeIfAbsent(rid) { sortedMapOf() }
        if (configs.put(height, Pair(conf, rawBcConf)) != null) {
            throw IllegalArgumentException("Setting blockchain configuration for height that already has a configuration")
        } else {
            awaitDebug("### NEW BC CONFIG for chain: ${nodeSet.chain} (bc rid: ${rid.toShortHex()}) at height: $height")
        }
        chainToNodeSet[ChainUtil.ridOf(nodeSet.chain)] = nodeSet
    }

    /**
     * This is to force a node to become totally unaware of a certain blockchain.
     */
    fun delBlockchain(rid: BlockchainRid) {
        bridToConfigs.remove(rid)
        extraReplicas.remove(rid)
        chainToNodeSet.remove(rid)
    }
}
