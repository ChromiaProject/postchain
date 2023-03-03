package net.postchain.devtools.mminfra

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.NodeRid
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSeqNumber
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.managed.BlockchainInfo
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.PendingBlockchainConfiguration

open class MockManagedNodeDataSource : ManagedNodeDataSource {
    // Brid -> (height -> Pair(BlockchainConfiguration, binaryBlockchainConfig)
    val bridToConfigs: MutableMap<BlockchainRid, MutableMap<Long, Pair<BlockchainConfiguration, ByteArray>>> = mutableMapOf()
    private val extraReplicas = mutableMapOf<BlockchainRid, MutableSet<NodeRid>>()
    private lateinit var nodes: Map<NodeSeqNumber, NodeSetup>
    private lateinit var myNode: NodeSetup

    fun addNodeSetup(nodeMap: Map<NodeSeqNumber, NodeSetup>, nodeSetup: NodeSetup) {
        nodes = nodeMap
        myNode = nodeSetup
    }

    override val nmApiVersion: Int
        get() = TODO("Not yet implemented")

    override fun getPeerListVersion(): Long {
        return 1L
    }

    override fun computeBlockchainList(): List<ByteArray> {
        return myNode.chainsToRead.union(myNode.chainsToSign).map { ChainUtil.ridOf(it.toLong()).data }
    }

    override fun computeBlockchainInfoList(): List<BlockchainInfo> {
        return computeBlockchainList().map {
            BlockchainInfo(
                    BlockchainRid(it),
                    false
            )
        }
    }

    override fun getLastBuiltHeight(blockchainRidRaw: ByteArray): Long {
        TODO("Not yet implemented")
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

    override fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): PendingBlockchainConfiguration? {
        TODO("Not yet implemented")
    }

    override fun isPendingBlockchainConfigurationApplied(blockchainRid: BlockchainRid, height: Long, baseConfigHash: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun query(name: String, args: Gtv): Gtv {
        TODO("Not yet implemented")
    }

    override fun getPeerInfos(): Array<PeerInfo> {
        return emptyArray()
    }

    override fun getSyncUntilHeight(): Map<BlockchainRid, Long> {
        return emptyMap()
    }

    override fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<NodeRid>> {
        if (!::nodes.isInitialized) return emptyMap()
        val result = mutableMapOf<BlockchainRid, List<NodeRid>>()
        bridToConfigs.keys.union(extraReplicas.keys).forEach {
            val chainId = ChainUtil.iidOf(it)
            val replicaSet = nodes.values.filter { node -> node.chainsToRead.contains(chainId.toInt()) }
            val replicas = replicaSet.map { replica -> NodeRid(replica.pubKeyHex.hexStringToByteArray()) }.toMutableSet()
            replicas.addAll(extraReplicas[it] ?: emptySet())
            result[it] = replicas.toList()
        }
        return result
    }

    fun addExtraReplica(brid: BlockchainRid, replica: NodeRid) {
        extraReplicas.computeIfAbsent(brid) { mutableSetOf() }.add(replica)
    }

    fun getBuiltConfiguration(chainId: Long, rawConfigurationData: ByteArray): BlockchainConfiguration {
        val brid = ChainUtil.ridOf(chainId)
        val configs = bridToConfigs[brid]!!
        return configs.values
                .first { cfgToRaw ->
                    cfgToRaw.second.contentEquals(rawConfigurationData)
                }.first
    }

    fun addConf(chainId: Long, rid: BlockchainRid, height: Long, conf: BlockchainConfiguration, rawBcConf: ByteArray) {
        val configs = bridToConfigs.computeIfAbsent(rid) { sortedMapOf() }
        if (configs.put(height, Pair(conf, rawBcConf)) != null) {
            throw IllegalArgumentException("Setting blockchain configuration for height that already has a configuration")
        } else {
            awaitDebug("### NEW BC CONFIG for chain: $chainId (bc rid: ${rid.toShortHex()}) at height: $height")
        }
    }

    /**
     * This is to force a node to become totally unaware of a certain blockchain.
     */
    fun delBlockchain(rid: BlockchainRid) {
        bridToConfigs.remove(rid)
        extraReplicas.remove(rid)
        val chainId = ChainUtil.iidOf(rid).toInt()
        myNode.removeChainToSign(chainId)
        myNode.removeChainToRead(chainId)
    }
}
