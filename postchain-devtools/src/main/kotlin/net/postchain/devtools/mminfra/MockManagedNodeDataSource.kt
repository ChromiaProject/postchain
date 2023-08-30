package net.postchain.devtools.mminfra

import net.postchain.base.PeerInfo
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainState
import net.postchain.core.NodeRid
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSeqNumber
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.managed.BlockchainInfo
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.PendingBlockchainConfiguration
import java.util.TreeMap

open class MockManagedNodeDataSource : ManagedNodeDataSource {
    // Brid -> (height -> Pair(BlockchainConfiguration, binaryBlockchainConfig)
    val bridToConfigs: MutableMap<BlockchainRid, MutableMap<Long, Pair<BlockchainConfiguration, ByteArray>>> = mutableMapOf()
    val pendingBridToConfigs: MutableMap<BlockchainRid, TreeMap<Long, MutableList<Pair<BlockchainConfiguration, ByteArray>>>> = mutableMapOf()
    val faultyConfigHashes: MutableMap<BlockchainRid, MutableMap<Long, ByteArray>> = mutableMapOf()
    val bridState: MutableMap<BlockchainRid, BlockchainState> = mutableMapOf()
    private val extraReplicas = mutableMapOf<BlockchainRid, MutableSet<NodeRid>>()
    private lateinit var nodes: Map<NodeSeqNumber, NodeSetup>
    private lateinit var myNode: NodeSetup

    fun addNodeSetup(nodeMap: Map<NodeSeqNumber, NodeSetup>, nodeSetup: NodeSetup) {
        nodes = nodeMap
        myNode = nodeSetup
    }

    override val nmApiVersion: Int
        get() = TODO("Not yet implemented")

    override fun computeBlockchainList(): List<ByteArray> {
        return myNode.chainsToRead.union(myNode.chainsToSign).map { ChainUtil.ridOf(it.toLong()).data }
    }

    override fun computeBlockchainInfoList(): List<BlockchainInfo> {
        return computeBlockchainList()
                .filter { bridState[BlockchainRid(it)] != BlockchainState.REMOVED }
                .map {
                    BlockchainInfo(
                            BlockchainRid(it),
                            false,
                            bridState[BlockchainRid(it)] ?: BlockchainState.RUNNING
                    )
                }
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

    override fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): List<PendingBlockchainConfiguration> {
        val allPendingConfigs = pendingBridToConfigs[blockchainRid] ?: return listOf()
        return allPendingConfigs.lowerEntry(height + 1)?.let { (height, configs) ->
            configs.map { PendingBlockchainConfiguration.fromBlockchainConfiguration(it.first, height) }
        } ?: listOf()
    }

    override fun getFaultyBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): ByteArray? {
        return faultyConfigHashes[blockchainRid]?.let { it[height] }
    }

    fun setBlockchainState(blockchainRid: BlockchainRid, blockchainState: BlockchainState) {
        bridState[blockchainRid] = blockchainState
    }

    override fun getBlockchainState(blockchainRid: BlockchainRid): BlockchainState {
        return bridState[blockchainRid]!!
    }

    override fun getBlockchainConfigurationOptions(blockchainRid: BlockchainRid, height: Long): BlockchainConfigurationOptions? = null

    override fun query(name: String, args: Gtv): Gtv {
        TODO("Not yet implemented")
    }

    override fun getPeerInfos(): Array<PeerInfo> {
        return emptyArray()
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

    open fun getBuiltConfiguration(chainId: Long, rawConfigurationData: ByteArray): BlockchainConfiguration {
        val brid = ChainUtil.ridOf(chainId)
        val configs = bridToConfigs[brid]!!
        val config = configs.values.firstOrNull { cfgToRaw ->
            cfgToRaw.second.contentEquals(rawConfigurationData)
        }
        return if (config != null) {
            config.first
        } else {
            val pendingConfigs = pendingBridToConfigs[brid]!!
            pendingConfigs.values.flatten().first { cfgToRaw ->
                cfgToRaw.second.contentEquals(rawConfigurationData)
            }.first
        }
    }

    fun addConf(chainId: Long, rid: BlockchainRid, height: Long, conf: BlockchainConfiguration, rawBcConf: ByteArray) {
        val configs = bridToConfigs.computeIfAbsent(rid) { sortedMapOf() }
        if (configs.put(height, Pair(conf, rawBcConf)) != null) {
            throw IllegalArgumentException("Setting blockchain configuration for height that already has a configuration")
        } else {
            awaitDebug("### NEW BC CONFIG for chain: $chainId (bc rid: ${rid.toShortHex()}) at height: $height")
            pendingBridToConfigs.clear()
        }
    }

    fun addPendingConf(chainId: Long, rid: BlockchainRid, height: Long, conf: BlockchainConfiguration, rawBcConf: ByteArray) {
        val configs = pendingBridToConfigs.computeIfAbsent(rid) { TreeMap() }
        configs.computeIfAbsent(height) { mutableListOf() }.add(conf to rawBcConf)
        awaitDebug("### NEW PENDING BC CONFIG for chain: $chainId (bc rid: ${rid.toShortHex()}) at height: $height")
    }

    fun markPendingConfigurationAsFaulty(chainId: Long, height: Long) {
        val brid = ChainUtil.ridOf(chainId)
        val config = pendingBridToConfigs[ChainUtil.ridOf(chainId)]!![height]!!.removeFirstOrNull()
        if (config != null) {
            faultyConfigHashes.computeIfAbsent(brid) { mutableMapOf() }[height] = config.first.configHash
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
