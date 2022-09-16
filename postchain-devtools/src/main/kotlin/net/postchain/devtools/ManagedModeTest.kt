package net.postchain.devtools

import mu.KLogging
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.configuration.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.NODE_ID_AUTO
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.mminfra.*
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtv.*
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.StandardOpsGTXModule
import java.lang.Thread.sleep

/**
 * This is still somewhat not in line with the [SystemSetup] architecture, defined in the parent class.
 *
 *
 */
open class ManagedModeTest : AbstractSyncTest() {

    private companion object : KLogging()

    val mockDataSources = mutableMapOf<Int, MockManagedNodeDataSource>()

    /**
     * Create a set of signers and replicas for the given chainID.
     */
    @Deprecated("Should conform with Setup arch. Use this: \"net.postchain.devtools.utils.configuration.NodeSetup\"")
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
        val brid = ChainUtil.ridOf(nodeSet.chain)

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
            data.setValue(KEY_SIGNERS, GtvArray(signerGtvs.toTypedArray()))
            if (historicChain != null) {
                data.setValue(KEY_HISTORIC_BRID, GtvByteArray(ChainUtil.ridOf(historicChain).data))
            }

            data.setValue(KEY_CONFIGURATIONFACTORY, GtvString(
                    GTXBlockchainConfigurationFactory::class.java.name
            ))

            data.setValue(KEY_BLOCKSTRATEGY, GtvDictionary.build(mapOf(
                    KEY_BLOCKSTRATEGY_MAXBLOCKTIME to GtvInteger(2_000)
            )))

            val gtx = mapOf(KEY_GTX_MODULES to GtvArray(arrayOf(
                    GtvString(StandardOpsGTXModule::class.java.name))
            ))
            data.setValue(KEY_GTX, GtvFactory.gtv(gtx))

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
            val confData = data.getDict().toObject<BlockchainConfigurationData>(mapOf("partialContext" to context, "sigmaker" to sigMaker))
            val bcConf = TestBlockchainConfiguration(confData)
            it.value.addConf(brid, height, bcConf, nodeSet, GtvEncoder.encodeGtv(data.getDict()))
        }
    }

    fun setupDataSources(nodeSet: NodeSet) {
        for (i in 0 until nodeSet.size) {
            if (!nodeSet.contains(i)) {
                throw IllegalStateException("We don't have node nr: $i")
            }
            val dataSource = createMockDataSource(i)
            mockDataSources.put(i, dataSource)
        }
        addBlockchainConfiguration(nodeSet, null, 0)
    }

    open fun createMockDataSource(nodeIndex: Int): MockDirectoryDataSource {
        return MockDirectoryDataSource(nodeIndex)
    }

    fun newBlockchainConfiguration(nodeSet: NodeSet, historicChain: Long?, height: Long, excludeChain0Nodes: Set<Int> = setOf()) {
        addBlockchainConfiguration(nodeSet, historicChain, height)
        // We need to build a block on c0 to trigger c0's restartHandler, otherwise
        // the node manager won't become aware of the new configuration
        buildBlock(c0.remove(excludeChain0Nodes))
    }

    protected open fun awaitChainRunning(index: Int, chainId: Long, atLeastHeight: Long) {
        val pm = nodes[index].processManager as TestManagedBlockchainProcessManager
        pm.awaitStarted(index, chainId, atLeastHeight)
    }

    fun restartNodeClean(index: Int, nodeSet: NodeSet, atLeastHeight: Long) {
        restartNodeClean(index, ChainUtil.ridOf(0))
        awaitChainRunning(index, nodeSet.chain, atLeastHeight)
    }

    fun buildBlock(nodeSet: NodeSet, toHeight: Long) {
        buildBlock(nodes.filterIndexed { i, _ -> nodeSet.contains(i) }, nodeSet.chain, toHeight)
    }

    fun buildBlock(nodeSet: NodeSet) {
        val currentHeight = nodeSet.nodes()[0].currentHeight(nodeSet.chain)
        buildBlock(nodeSet, currentHeight + 1)
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
            if (it.blockQueries(nodeSet.chain).getBestHeight().get() >= height) throw RuntimeException("assertCantBuildBlock: Can build block")
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


    protected open fun awaitChainRestarted(nodeSet: NodeSet, atLeastHeight: Long) {
        nodeSet.all().forEach { awaitChainRunning(it, nodeSet.chain, atLeastHeight) }
    }

    private var chainId: Long = 1
    fun startNewBlockchain(
            signers: Set<Int>,
            replicas: Set<Int>,
            historicChain: Long? = null,
            excludeChain0Nodes: Set<Int> = setOf(),
            waitForRestart: Boolean = true
    ): NodeSet {
        if (signers.intersect(replicas).isNotEmpty()) throw IllegalArgumentException("a node cannot be both signer and replica")
        val maxIndex = c0.all().size
        signers.forEach {
            if (it >= maxIndex) throw IllegalArgumentException("bad signer index")
        }
        replicas.forEach {
            if (it >= maxIndex) throw IllegalArgumentException("bad replica index")
        }
        val c = NodeSet(chainId++, signers, replicas)
        newBlockchainConfiguration(c, historicChain, 0, excludeChain0Nodes)
        // Await blockchain started on all relevant nodes
        if (waitForRestart)
            awaitChainRestarted(c, -1)
        return c
    }
}


const val awaitDebugLog = false

/**
 * Sometimes we want to monitor how long we are waiting and WHAT we are weighting for, then we can turn on this flag.
 * Using System.out to separate this from "real" logs
 */
fun awaitDebug(dbg: String) {
    if (awaitDebugLog) {
        println("TEST: $dbg")
    }
}
