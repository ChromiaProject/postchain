package net.postchain.integrationtest.sync

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockTrace
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.currentHeight
import net.postchain.devtools.testinfra.TestTransactionFactory
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.integrationtest.sync.ManagedModeTest.NodeSet
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedEBFTInfrastructureFactory
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.network.x.XPeerID
import java.lang.Thread.sleep
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertTrue

open class ManagedModeTest : AbstractSyncTest() {

    private companion object: KLogging()
    val mockDataSources = mutableMapOf<Int, MockManagedNodeDataSource>()

    inner class NodeSet(val chain: Long, val signers: Set<Int>, val replicas: Set<Int>) {
        val size: Int = signers.size + replicas.size
        fun contains(i: Int) = signers.contains(i) || replicas.contains(i)
        fun all(): Set<Int> = signers.union(replicas)
        fun nodes() = nodes.filterIndexed { i, p -> contains(i) }
        /**
         * Creates a new NodeSet as a copy of this NodeSet, but
         * with some nodes removed
         */
        fun remove(nodesToRemove: Set<Int>): ManagedModeTest.NodeSet {
            return NodeSet(chain, signers.minus(nodesToRemove), replicas.minus(nodesToRemove))
        }
    }

    fun dataSources(nodeSet: NodeSet): Map<Int, MockManagedNodeDataSource> {
        return mockDataSources.filterKeys { nodeSet.contains(it) }
    }

    fun addBlockchainConfiguration(nodeSet: NodeSet, historicChain: Long?, height: Long) {
        val brid = chainRidOf(nodeSet.chain)

        val signerGtvs = mutableListOf<GtvByteArray>()
        if (nodeSet.chain == 0L) {
            nodeSet.signers.forEach {
                signerGtvs.add(GtvByteArray(KeyPairHelper.pubKey(it)))
            }
        } else {
            nodeSet.signers.forEach {
                signerGtvs.add(GtvByteArray(nodes[it].pubKey.hexStringToByteArray()))
            }
        }

        mockDataSources.forEach {
            val data = TestBlockchainConfigurationData()
            data.setValue(BaseBlockchainConfigurationData.KEY_SIGNERS, GtvArray(signerGtvs.toTypedArray()))
            if (historicChain != null) {
                data.setValue(BaseBlockchainConfigurationData.KEY_HISTORIC_BRID, GtvByteArray(chainRidOf(historicChain).data))
            }
            val pubkey = if (nodeSet.chain == 0L) {
                if (it.key < nodeSet.signers.size) {
                    KeyPairHelper.pubKey(it.key)
                } else {
                    KeyPairHelper.pubKey(-1 - it.key)
                }
            } else {
                nodes[it.key].pubKey.hexStringToByteArray()
            }

            val context = BaseBlockchainContext(brid, NODE_ID_AUTO, nodeSet.chain, pubkey)

            val privkey = KeyPairHelper.privKey(pubkey)
            val sigMaker = cryptoSystem.buildSigMaker(pubkey, privkey)
            val confData = BaseBlockchainConfigurationData(data.getDict(), context, sigMaker)
            val bcConf = TestBlockchainConfiguration(confData)
            it.value.addConf(brid, height, bcConf, nodeSet)
        }
    }

    fun setupDataSources(nodeSet: NodeSet) {
        for (i in 0 until nodeSet.size) {
            if (!nodeSet.contains(i)) {
                throw IllegalStateException("We don't have node nr: " + i)
            }
            val dataSource = MockManagedNodeDataSource(i)
            mockDataSources.put(i, dataSource)
        }
        addBlockchainConfiguration(nodeSet, null, 0)
    }

    fun newBlockchainConfiguration(nodeSet: NodeSet, historicChain: Long?, height: Long, excludeChain0Nodes: Set<Int> = setOf()) {
        addBlockchainConfiguration(nodeSet, historicChain, height)
        // We need to build a block on c0 to trigger c0's restartHandler, otherwise
        // the node manager won't become aware of the new configuration
        buildBlock(c0.remove(excludeChain0Nodes))
    }

    protected fun awaitChainRunning(index: Int, chainId: Long, atLeastHeight: Long) {
        val pm = nodes[index].processManager as TestManagedBlockchainProcessManager
        pm.awaitStarted(index, chainId, atLeastHeight)
    }

    fun restartNodeClean(index: Int, nodeSet: NodeSet, atLeastHeight: Long) {
        restartNodeClean(index, chainRidOf(0))
        awaitChainRunning(index, nodeSet.chain, atLeastHeight)
    }

    fun buildBlock(nodeSet: NodeSet, toHeight: Long) {
        buildBlock(nodes.filterIndexed { i,p -> nodeSet.contains(i) }, nodeSet.chain.toLong(), toHeight)
    }

    fun buildBlock(nodeSet: NodeSet) {
        val currentHeight = nodeSet.nodes()[0].currentHeight(nodeSet.chain)
        buildBlock(nodeSet, currentHeight+1)
    }

    fun awaitHeight(nodeSet: NodeSet, height: Long) {
        awaitLog("========= AWAIT ALL ${nodeSet.size} NODES chain:  ${nodeSet.chain}, height:  $height")
        awaitHeight(nodeSet.nodes(), nodeSet.chain, height)
        awaitLog("========= DONE AWAIT ALL ${nodeSet.size} NODES chain: ${nodeSet.chain}, height: $height")
    }

    fun assertCantBuildBlock(nodeSet: NodeSet, height: Long) {
        buildBlockNoWait(nodeSet.nodes(), nodeSet.chain, height)
        sleep(1000)
        nodeSet.nodes().forEach {
            assertTrue(it.blockQueries(nodeSet.chain).getBestHeight().get() < height)
        }
    }

    /**
     * In this case we want unique configs per node (the mock datasource)
     */
    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        var className = TestManagedEBFTInfrastructureFactory::class.qualifiedName
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", className)
        nodeSetup.nodeSpecificConfigs.setProperty(
            "infrastructure.datasource",
            mockDataSources[nodeSetup.sequenceNumber.nodeNumber]
        )
    }

    lateinit var c0: NodeSet
    fun startManagedSystem(signers: Int, replicas: Int) {
        c0 = NodeSet(0, (0 until signers).toSet(), (signers until signers + replicas).toSet())
        setupDataSources(c0)
        runNodes(c0.signers.size, c0.replicas.size)
        buildBlock(c0, 0)
    }


    class TestBlockchainConfigurationData {
        private val m = mutableMapOf<String, Gtv>()
        fun getDict(): GtvDictionary {
            return GtvDictionary.build(m)
        }

        fun setValue(key: String, value: Gtv) {
            m[key] = value
        }
    }


    class TestBlockchainConfiguration(data: BaseBlockchainConfigurationData):
            BaseBlockchainConfiguration(data) {
        override fun getTransactionFactory(): TransactionFactory {
            return TestTransactionFactory()
        }

        override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy {
            return OnDemandBlockBuildingStrategy(configData, this, blockQueries, txQueue)
        }
    }
}

class TestManagedEBFTInfrastructureFactory : ManagedEBFTInfrastructureFactory() {
    lateinit var nodeConfig: NodeConfig
    lateinit var dataSource: MockManagedNodeDataSource
    override fun makeProcessManager(nodeConfigProvider: NodeConfigurationProvider,
                                    blockchainInfrastructure: BlockchainInfrastructure,
                                    blockchainConfigurationProvider: BlockchainConfigurationProvider,
                                    nodeDiagnosticContext: NodeDiagnosticContext): BlockchainProcessManager {
        return TestManagedBlockchainProcessManager(blockchainInfrastructure, nodeConfigProvider,
                blockchainConfigurationProvider, nodeDiagnosticContext, dataSource)
    }

    override fun makeBlockchainInfrastructure(nodeConfigProvider: NodeConfigurationProvider,
                                              nodeDiagnosticContext: NodeDiagnosticContext): BlockchainInfrastructure {
        nodeConfig = nodeConfigProvider.getConfiguration()
        dataSource = nodeConfig.appConfig.config.get(MockManagedNodeDataSource::class.java, "infrastructure.datasource")!!

        val syncInfra = EBFTSynchronizationInfrastructure(nodeConfigProvider, nodeDiagnosticContext)
        val apiInfra = BaseApiInfrastructure(nodeConfigProvider, nodeDiagnosticContext)
        val infrastructure = TestManagedBlockchainInfrastructure(nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext, dataSource)
        return infrastructure
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return TestManagedBlockchainConfigurationProvider(dataSource)
    }
}


/**
 * We've overridden ALL methods of the [ManagedBlockchainConfigurationProvider] so we will never use the "real" data source.
 */
class TestManagedBlockchainConfigurationProvider(val mockDataSource: ManagedNodeDataSource):
    ManagedBlockchainConfigurationProvider() {

    companion object: KLogging()

    override fun getConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        val db = DatabaseAccess.of(eContext)
        val height = db.getLastBlockHeight(eContext)
        return mockDataSource.getConfiguration(chainRidOf(chainId).data, height+1)
    }

    override fun needsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        val dba = DatabaseAccess.of(eContext)
        val height = dba.getLastBlockHeight(eContext)
        val blockchainRid = chainRidOf(chainId)
        val nextConfigHeight = mockDataSource.findNextConfigurationHeight(blockchainRid.data, height)
        logger.debug("needsConfigurationChange() - height: $height, next conf at: $nextConfigHeight")
        return (nextConfigHeight != null) && (nextConfigHeight == height + 1)
    }
}


class TestManagedBlockchainInfrastructure(nodeConfigProvider: NodeConfigurationProvider,
                                          syncInfra: SynchronizationInfrastructure, apiInfra: ApiInfrastructure,
                                          nodeDiagnosticContext: NodeDiagnosticContext, val mockDataSource: MockManagedNodeDataSource) :
        BaseBlockchainInfrastructure(nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext) {
    override fun makeBlockchainConfiguration(rawConfigurationData: ByteArray, eContext: EContext, nodeId: Int, chainId: Long): BlockchainConfiguration {
        return mockDataSource.getConf(rawConfigurationData)!!
    }
}

class TestManagedBlockchainProcessManager(blockchainInfrastructure: BlockchainInfrastructure,
                                          nodeConfigProvider: NodeConfigurationProvider,
                                          blockchainConfigProvider: BlockchainConfigurationProvider,
                                          nodeDiagnosticContext: NodeDiagnosticContext,
                                          val dataSource: ManagedNodeDataSource)
    : ManagedBlockchainProcessManager(blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext) {

    companion object : KLogging()

    private val blockchainStarts = ConcurrentHashMap<Long, BlockingQueue<Long>>()

    override fun buildChain0ManagedDataSource(): ManagedNodeDataSource {
        return dataSource
    }

    /**
     * Overriding the original method, so that we now, instead of checking the DB for what
     * BCs to launch we instead
     */
    override fun retrieveBlockchainsToLaunch(): Array<Long> {
        retrieveDebug("NOTE TEST! - Begin ")
        val result = mutableListOf<Long>()
        dataSource.computeBlockchainList().forEach {
            val brid = BlockchainRid(it)
            val chainIid = chainIidOf(brid)
            result.add(chainIid)
            retrieveDebug("NOTE TEST! -- launch chainIid: $chainIid,  BC RID: ${brid.toShortHex()} ")
            withReadWriteConnection(storage, chainIid) { newCtx ->
                DatabaseAccess.of(newCtx).initializeBlockchain(newCtx, brid)
            }
        }
        retrieveDebug("NOTE TEST! - End, restart: ${result.size} ")
        return result.toTypedArray()
    }

    private fun getQueue(chainId: Long): BlockingQueue<Long> {
        return blockchainStarts.computeIfAbsent(chainId) {
            LinkedBlockingQueue<Long>()
        }
    }

    // Marks the BC height directly after the last BC restart.
    // (The ACTUAL BC height will often proceed beyond this height, but we don't track that here)
    var lastHeightStarted = ConcurrentHashMap<Long, Long>()

    /**
     * Overriding the original startBlockchain() and adding extra logic for measuring restarts.
     *
     * (This method will run for for every new height where we have a new BC configuration,
     * b/c the BC will get restarted before the configuration can be used.
     * Every time this method runs the [lastHeightStarted] gets updated with the restart height.)
     */
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid? {
        val blockchainRid = super.startBlockchain(chainId, bTrace)
        if (blockchainRid == null) {
            return null
        }
        val process = blockchainProcesses[chainId]!!
        val queries = process.getEngine().getBlockQueries()
        val height = queries.getBestHeight().get()
        lastHeightStarted[chainId] = height
        return blockchainRid
    }

    /**
     * Awaits a start/restart of a BC.
     *
     * @param nodeIndex the node we should wait for
     * @param chainId the chain we should wait for
     * @param atLeastHeight the height we should wait for. Note that this height MUST be a height where we have a
     *           new BC configuration kicking in, because that's when the BC will be restarted.
     *           Example: if a new BC config starts at height 10, then we should put [atLeastHeight] to 9.
     */
    fun awaitStarted(nodeIndex: Int, chainId: Long, atLeastHeight: Long) {
        awaitDebug("++++++ AWAIT node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
        while (lastHeightStarted.get(chainId) ?: -2L < atLeastHeight) {
            sleep(10)
        }
        awaitDebug("++++++ WAIT OVER! node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
    }
}

val awaitDebugLog = false

/**
 * Sometimes we want to monitor how long we are waiting and WHAT we are weighting for, then we can turn on this flag.
 * Using System.out to separate this from "real" logs
 */
fun awaitDebug(dbg: String) {
    if (awaitDebugLog) {
        System.out.println("TEST: $dbg")
    }
}

fun chainRidOf(chainIid: Long): BlockchainRid {
    val hexChainIid = chainIid.toString(8)
    val base = "0000000000000000000000000000000000000000000000000000000000000000"
    val rid = base.substring(0, 64-hexChainIid.length) + hexChainIid
    return BlockchainRid.buildFromHex(rid)
}

fun chainIidOf(brid: BlockchainRid): Long {
    return brid.toHex().toLong(8)
}

typealias Key = Pair<BlockchainRid, Long>



class MockManagedNodeDataSource(val nodeIndex: Int) : ManagedNodeDataSource {
    // Brid -> (height -> Pair<BlockchainConfiguration, NodeSet>)
    private val bridToConfs: MutableMap<BlockchainRid, MutableMap<Long, BlockchainConfiguration>> = mutableMapOf()
    private val chainToNodeSet: MutableMap<BlockchainRid, NodeSet> = mutableMapOf()
    private val extraReplicas = mutableMapOf<BlockchainRid, MutableSet<XPeerID>>()

    override fun getPeerListVersion(): Long {
        return 1L
    }

    override fun computeBlockchainList(): List<ByteArray> {
        return chainToNodeSet.filterValues { it.contains(nodeIndex) }.keys.map { it.data }
    }

    override fun getConfiguration(blockchainRIDRaw: ByteArray, height: Long): ByteArray? {
        val l = bridToConfs[BlockchainRid(blockchainRIDRaw)] ?: return null
        var conf: ByteArray? = null
        for (entry in l) {
            if (entry.key <= height) {
                conf = toByteArray(Key(BlockchainRid(blockchainRIDRaw), entry.key))
            } else {
                return conf
            }
        }
        return conf
    }

    override fun findNextConfigurationHeight(blockchainRIDRaw: ByteArray, height: Long): Long? {
        val l = bridToConfs[BlockchainRid(blockchainRIDRaw)] ?: return null
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

    override fun getNodeReplicaMap(): Map<XPeerID, List<XPeerID>> {
        return mapOf()
    }

    override fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<XPeerID>> {
        val result = mutableMapOf<BlockchainRid, List<XPeerID>>()
        chainToNodeSet.keys.union(extraReplicas.keys).forEach {
            val replicaSet = chainToNodeSet[it]?.replicas ?: emptySet()
            var replicas = replicaSet.map { XPeerID(KeyPairHelper.pubKey(it)) }.toMutableSet()
            replicas.addAll(extraReplicas[it] ?: emptySet())
            result.put(it, replicas.toList())
        }
        return result
    }

    fun addExtraReplica(brid: BlockchainRid, replica: XPeerID) {
        extraReplicas.computeIfAbsent(brid) { mutableSetOf<XPeerID>() }.add(replica)
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
        val height =  bytes.copyOfRange(32, bytes.size).toHex().toLong(8)
        return Key(rid, height)
    }

    fun getConf(bytes: ByteArray): BlockchainConfiguration? {
        val key = toKey(bytes)
        return bridToConfs[key.first]?.get(key.second)
    }

    fun addConf(rid: BlockchainRid, height: Long, conf: BlockchainConfiguration, nodeSet: NodeSet) {
        val confs = bridToConfs.computeIfAbsent(rid) { sortedMapOf()}
        if (confs!!.put(height, conf) != null) {
            throw IllegalArgumentException("Setting blockchain configuraion for height that already has a configuration")
        } else {
            awaitDebug("### NEW BC CONFIG for chain: ${nodeSet.chain} (bc rid: ${rid.toShortHex()}) at height: $height")
        }
        chainToNodeSet.put(chainRidOf(nodeSet.chain), nodeSet)
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