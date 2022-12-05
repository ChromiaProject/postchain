package net.postchain.devtools

import mu.KLogging
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.configuration.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withWriteConnection
import net.postchain.common.hexStringToByteArray
import net.postchain.core.NODE_ID_AUTO
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.mminfra.*
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSeqNumber
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtv.*
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.StandardOpsGTXModule
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import java.lang.Thread.sleep

/**
 * This is still somewhat not in line with the [SystemSetup] architecture, defined in the parent class.
 *
 *
 */
open class ManagedModeTest : AbstractSyncTest() {

    private companion object : KLogging()

    val c0 = 0L
    val mockDataSources = mutableMapOf<Int, MockManagedNodeDataSource>()

    fun dataSources(chainId: Long): Map<Int, MockManagedNodeDataSource> {
        val nodeIdsInChain = getChainNodeSetups(chainId).map { it.sequenceNumber.nodeNumber }
        return mockDataSources.filterKeys { nodeIdsInChain.contains(it) }
    }

    fun addBlockchainConfiguration(chainId: Long, signerKeys: Map<Int, ByteArray>, historicChain: Long?, height: Long) {
        val brid = ChainUtil.ridOf(chainId)

        val signerGtvs = signerKeys.values.map { GtvByteArray(it) }

        val data = TestBlockchainConfigurationData()
        data.setValue(KEY_SIGNERS, GtvArray(signerGtvs.toTypedArray()))
        if (historicChain != null) {
            data.setValue(KEY_HISTORIC_BRID, GtvByteArray(ChainUtil.ridOf(historicChain).data))
        }

        data.setValue(KEY_CONFIGURATIONFACTORY, GtvString(GTXBlockchainConfigurationFactory::class.java.name))

        data.setValue(KEY_BLOCKSTRATEGY, GtvDictionary.build(mapOf(
                KEY_BLOCKSTRATEGY_MAXBLOCKTIME to GtvInteger(2_000)
        )))

        val gtx = mapOf(KEY_GTX_MODULES to GtvArray(arrayOf(
                GtvString(StandardOpsGTXModule::class.java.name))
        ))
        data.setValue(KEY_GTX, GtvFactory.gtv(gtx))

        mockDataSources.forEach {
            val pubkey = if (chainId == 0L) {
                signerKeys[it.key] ?: KeyPairHelper.pubKey(-1 - it.key)
            } else {
                nodes[it.key].pubKey.hexStringToByteArray()
            }
            val sigMaker = createSigMaker(pubkey)

            val context = BaseBlockchainContext(brid, NODE_ID_AUTO, chainId, pubkey)
            val confData = data.getDict().toObject<BlockchainConfigurationData>(mapOf("partialContext" to context, "sigmaker" to sigMaker))
            val bcConf = TestBlockchainConfiguration(confData, it.value)
            it.value.addConf(chainId, brid, height, bcConf, GtvEncoder.encodeGtv(data.getDict()))
        }
    }

    fun setupDataSources(nrOfNodes: Int) {
        for (i in 0 until nrOfNodes) {
            val dataSource = MockManagedNodeDataSource()
            mockDataSources[i] = dataSource
        }
    }

    protected open fun awaitChainRunning(index: Int, chainId: Long, atLeastHeight: Long) {
        val pm = nodes[index].processManager as TestManagedBlockchainProcessManager
        pm.awaitStarted(index, chainId, atLeastHeight)
    }

    fun restartNodeClean(index: Int, chainId: Long, atLeastHeight: Long) {
        restartNodeClean(index, ChainUtil.ridOf(0))
        awaitChainRunning(index, chainId, atLeastHeight)
    }

    override fun updateCache(nodeSetup: NodeSetup, testNode: PostchainTestNode) {
        super.updateCache(nodeSetup, testNode)
        mockDataSources.forEach { it.value.addNodeSetup(systemSetup.nodeMap, systemSetup.nodeMap[NodeSeqNumber(it.key)]!!) }
    }

    fun assertCantBuildBlock(chainId: Long, height: Long) {
        val chainNodes = getChainNodes(chainId)
        buildBlockNoWait(chainNodes, chainId, height)
        sleep(1000)
        chainNodes.forEach {
            if (it.blockQueries(chainId).getBestHeight().get() >= height) throw RuntimeException("assertCantBuildBlock: Can build block")
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

    fun startManagedSystem(signers: Int, replicas: Int) {
        setupDataSources(signers + replicas)
        val signerKeys = (0 until signers).associateWith { KeyPairHelper.pubKey(it) }
        addBlockchainConfiguration(0, signerKeys, null, 0)
        runNodes(signers, replicas)
        mockDataSources.forEach { it.value.addNodeSetup(systemSetup.nodeMap, systemSetup.nodeMap[NodeSeqNumber(it.key)]!!) }
        buildBlock(0, 0)
    }


    protected open fun awaitChainRestarted(chainId: Long, atLeastHeight: Long) {
        val nodeSetups = getChainNodeSetups(chainId)
        awaitLog("========= AWAIT ALL ${nodeSetups.size} NODES RESTART chain:  ${chainId}, at least height:  $atLeastHeight")
        nodeSetups.forEach { awaitChainRunning(it.sequenceNumber.nodeNumber, chainId, atLeastHeight) }
        awaitLog("========= DONE WAITING ALL ${nodeSetups.size} NODES RESTART chain:  ${chainId}, at least height:  $atLeastHeight")
    }

    private var chainId: Long = 1
    fun startNewBlockchain(
            signers: Set<Int>,
            replicas: Set<Int>,
            historicChain: Long? = null,
            excludeChain0Nodes: Set<Int> = setOf(),
            waitForRestart: Boolean = true,
            rawBlockchainConfiguration: ByteArray? = null,
            blockchainConfigurationFactory: GTXBlockchainConfigurationFactory? = null
    ): Long {
        if (signers.intersect(replicas).isNotEmpty()) throw IllegalArgumentException("a node cannot be both signer and replica")
        val newChainId = chainId++
        if (rawBlockchainConfiguration != null) {
            val brid = ChainUtil.ridOf(newChainId)
            mockDataSources.forEach { (nodeId, dataSource) ->
                val pubkey = nodes[nodeId].pubKey.hexStringToByteArray()
                val sigMaker = createSigMaker(pubkey)

                val bcConf = BlockchainConfigurationData.fromRaw(rawBlockchainConfiguration, brid, NODE_ID_AUTO, newChainId, pubkey, sigMaker)
                val bcFactory = blockchainConfigurationFactory ?: GTXBlockchainConfigurationFactory()
                val dappBcFactory = DappBlockchainConfigurationFactory(bcFactory, dataSource)
                val postchainContext = nodes[nodeId].postchainContext
                withWriteConnection(postchainContext.storage, newChainId) { ctx ->
                    DatabaseAccess.of(ctx).apply { initializeBlockchain(ctx, brid) }
                    dataSource.addConf(newChainId, brid, 0, dappBcFactory.makeBlockchainConfiguration(bcConf, ctx, postchainContext.cryptoSystem), rawBlockchainConfiguration)
                    true
                }
            }
        } else {
            val signerKeys = signers.associateWith { nodes[it].pubKey.hexStringToByteArray() }
            addBlockchainConfiguration(newChainId, signerKeys, historicChain, 0)
        }

        setChainSigners(signers, newChainId)
        setChainReplicas(replicas, newChainId)
        // We need to build a block on c0 to trigger c0's restartHandler, otherwise
        // the node manager won't become aware of the new configuration
        val chain0Nodes = nodes.filterIndexed { i, _ -> !excludeChain0Nodes.contains(i) }
        buildBlock(chain0Nodes, 0)
        // Await blockchain started on all relevant nodes
        if (waitForRestart)
            awaitChainRestarted(newChainId, -1)
        return newChainId
    }

    protected fun setChainSigners(signers: Set<Int>, chainId: Long) {
        systemSetup.nodeMap.forEach {
            if (signers.contains(it.key.nodeNumber)) {
                it.value.addChainToSign(chainId.toInt())
            } else {
                it.value.removeChainToSign(chainId.toInt())
            }
        }
    }

    protected fun setChainReplicas(replicas: Set<Int>, chainId: Long) {
        systemSetup.nodeMap.forEach {
            if (replicas.contains(it.key.nodeNumber)) {
                it.value.addChainToRead(chainId.toInt())
            } else {
                it.value.removeChainToRead(chainId.toInt())
            }
        }
    }

    private fun createSigMaker(pubkey: ByteArray): SigMaker {
        val privkey = KeyPairHelper.privKey(pubkey)
        return cryptoSystem.buildSigMaker(KeyPair(pubkey, privkey))
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
