// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.Transaction
import net.postchain.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.utils.configuration.*
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.ebft.worker.ValidatorWorker
import org.apache.commons.configuration2.MapConfiguration
import org.awaitility.kotlin.await
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import java.net.ServerSocket

/**
 * This class uses the [SystemSetup] helper class to construct tests, and this way skips node config files, but
 * may or may not use blockchain config files.
 *
 * If you need to provide unusual configurations in your config files, you can use [ConfigFileBasedIntegrationTest]
 * instead (but usually we can sneak in settings via [configOverrides] etc  even here, so it SHOULDN'T be needed)
 */
open class IntegrationTestSetup : AbstractIntegration() {

    protected lateinit var systemSetup: SystemSetup
    protected val nodes = mutableListOf<PostchainTestNode>()
    protected val nodeMap = mutableMapOf<NodeSeqNumber, PostchainTestNode>()
    val configOverrides = MapConfiguration(mutableMapOf<String, String>())

    // PeerInfos must be shared between all nodes because
    // a listening node will update the PeerInfo port after
    // ServerSocket is created.
    private var peerInfos: Array<PeerInfo>? = null
    private var expectedSuccessRids = mutableMapOf<Long, MutableList<ByteArray>>()

    companion object : KLogging()

    val awaitDebugLog = false

    /**
     * If we want to monitor how long we are waiting and WHAT we are waiting for, then we can turn on this flag.
     *
     * NOTE: The reason we do simple System Out is because running multiple nodes with a common logfile
     * and enforced waiting is a situation unique for tests, so it's better if these "logs" look different from "real" logs.
     */
    fun awaitLog(dbg: String) {
        if (awaitDebugLog) {
            System.out.println("TEST: $dbg")
        }
    }

    // For important test info we always want to log
    fun testLog(dbg: String) {
        System.out.println("TEST: $dbg")
    }

    @After
    override fun tearDown() {
        try {
            logger.debug("Integration test -- TEARDOWN")
            nodes.forEach { it.shutdown() }
            nodes.clear()
            nodeMap.clear()
            logger.debug("Closed nodes")
            peerInfos = null
            expectedSuccessRids = mutableMapOf()
            configOverrides.clear()
            TestBlockchainRidCache.clear()
            logger.debug("teadDown() done")
        } catch (t: Throwable) {
            logger.error("tearDown() failed", t)
        }
    }

    /**
     * Easy to forget we have two caches (due to legacy, maybe a bad practice?)
     */
    protected fun updateCache(nodeSetup: NodeSetup, testNode: PostchainTestNode) {
        nodeMap[nodeSetup.sequenceNumber] = testNode
        val nodeId = nodeSetup.sequenceNumber.nodeNumber

        if (nodeId < nodes.size) {
            nodes[nodeId] = testNode // We'll overwrite the existing test node
        } else {
            nodes.add(nodeId, testNode) // We'll add the test node to the list, but should be on the correct position.
        }
    }

    protected fun strategy(node: PostchainTestNode): OnDemandBlockBuildingStrategy {
        return node
            .getBlockchainInstance()
            .getEngine()
            .getBlockBuildingStrategy() as OnDemandBlockBuildingStrategy
    }

    // TODO: [et]: Check out nullability for return value
    protected fun enqueueTx(node: PostchainTestNode, data: ByteArray, expectedConfirmationHeight: Long): Transaction? {
        val blockchainEngine = node.getBlockchainInstance().getEngine()
        val tx = blockchainEngine.getConfiguration().getTransactionFactory().decodeTransaction(data)
        blockchainEngine.getTransactionQueue().enqueue(tx)

        if (expectedConfirmationHeight >= 0) {
            expectedSuccessRids.getOrPut(expectedConfirmationHeight) { mutableListOf() }
                    .add(tx.getRID())
        }

        return tx
    }

    protected fun verifyBlockchainTransactions(node: PostchainTestNode) {
        val expectAtLeastHeight = expectedSuccessRids.keys.reduce { acc, l -> maxOf(l, acc) }
        val bestHeight = getBestHeight(node)
        assertTrue(bestHeight >= expectAtLeastHeight)
        for (height in 0..bestHeight) {
            val txRidsAtHeight = getTxRidsAtHeight(node, height)

            val expectedRidsAtHeight = expectedSuccessRids[height]
            if (expectedRidsAtHeight == null) {
                assertArrayEquals(arrayOf(), txRidsAtHeight)
            } else {
                assertArrayEquals(expectedRidsAtHeight.toTypedArray(), txRidsAtHeight)
            }
        }
    }


    /**
     * Creates [count] nodes with the same configuration.
     * (The nodes needed will be fetched from the signer list.)
     *
     * @param nodesCount is the expected number of nodes. Only here for extra validation( make sure it is correct)
     * @param blockchainConfigFilename is the file holding the blockchain's configuration
     * @param preWipeDatabase should we wipe the db before test
     * @param setupAction is the action to perform on the [AppConfig] and [NodeConfig]
     * @return an array of nodes
     */
    protected fun createNodes(
            nodesCount: Int,
            blockchainConfigFilename: String,
            preWipeDatabase: Boolean = true,
            setupAction: (appConfig: AppConfig, nodeConfig: NodeConfig) -> Unit = { _, _ -> Unit }
    ): Array<PostchainTestNode> {

        // 1. Build the BC Setup
        val blockchainGtvConfig = readBlockchainConfig(blockchainConfigFilename)
        val chainId = 1 // We only have one.
        val blockchainSetup = BlockchainSetupFactory.buildFromGtv(chainId, blockchainGtvConfig)

        // 2. Build the system Setup
        val sysSetup = SystemSetupFactory.buildSystemSetup(listOf(blockchainSetup))

        if (nodesCount != sysSetup.nodeMap.size) {
            throw IllegalArgumentException("The blockchain conf expected ${sysSetup.nodeMap.size} signers, but you expected: $nodesCount")
        }

        // 3. Create the configuration provider
        createNodesFromSystemSetup(sysSetup, preWipeDatabase, setupAction)
        return nodes.toTypedArray()
    }

    /**
     * Used to create the [PostchainTestNode] 's needed for this test.
     * Will start the nodes and all chains on them.
     *
     * @param systemSetup is the map of what the test setup looks like.
     */
    protected fun createNodesFromSystemSetup(
        sysSetup: SystemSetup,
        preWipeDatabase: Boolean = true,
        setupAction: (appConfig: AppConfig, nodeConfig: NodeConfig) -> Unit = { _, _ -> Unit }
    ) {
        this.systemSetup = sysSetup

        // 1. generate node config for all [NodeSetup]
        createNodeConfProvidersAndAddToNodeSetup(sysSetup, configOverrides, setupAction)

        // 2. start all nodes and all chains
        val testNodeMap = startAllTestNodesAndAllChains(sysSetup, preWipeDatabase)
        for (nodeId in testNodeMap.keys) {
            val tmpNode = testNodeMap[nodeId]!!
            updateCache(sysSetup.nodeMap[nodeId]!!, tmpNode)
        }

        // 3. FastSynch
        awaitFastSynch(sysSetup, testNodeMap)
    }

    /**
     * (Kalle's description)
     * Await FastSynch to complete. This is to prevent a situation where:
     * 1. Test starts all nodes
     * 2. post a transaction
     * 3. await block 0 (with timeout of 10 seconds)
     * 4. Fastsync doesn't succeed within a timeout of X>10 seconds
     *
     * This can happen in rare occations. It's common enough to happen at avery other -Pci build on travis.
     * This code waits for sync on all signer nodes before returning in step 1.
     */
    protected fun awaitFastSynch(
        sysSetup: SystemSetup,
        testNodeMap: Map<NodeSeqNumber, PostchainTestNode>
    ) {
        sysSetup.nodeMap.values.forEach { nodeSetup ->
            nodeSetup.chainsToSign.forEach { chainIid ->
                val process = testNodeMap[nodeSetup.sequenceNumber]!!.getBlockchainInstance(chainIid.toLong())
                await.until {
                    if (process is ValidatorWorker) {
                        !process.syncManager.isInFastSync()
                    } else {
                        true
                    }
                }
            }
        }
    }

    /**
     * Create the [PostchainTestNode], start everything, and return them
     */
    protected fun startAllTestNodesAndAllChains(
        sysSetup: SystemSetup,
        preWipeDatabase: Boolean
    ): Map<NodeSeqNumber, PostchainTestNode> {
        val retMap = mutableMapOf<NodeSeqNumber, PostchainTestNode>()
        for (nodeSetup in sysSetup.nodeMap.values) {
            retMap[nodeSetup.sequenceNumber] = nodeSetup.toTestNodeAndStartAllChains(systemSetup, preWipeDatabase)
        }
        return retMap.toMap()
    }

    /**
     * @return subclass name or dummy
     */
    protected fun getTestName() = this::class.java.simpleName ?: "NoName"

    /**
     * Override this in your test to add config overrides directly on the [NodeSetup] (for node specific configs).
     */
    open fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {}

    /**
     * Generates config for all [NodeSetup] objects
     */
    protected fun createNodeConfProvidersAndAddToNodeSetup(
        sysSetup: SystemSetup,
        confOverrides: MapConfiguration,
        setupAction: (appConfig: AppConfig, nodeConfig: NodeConfig) -> Unit = { _, _ -> Unit }
    ) {
        val peerList = sysSetup.toPeerInfoList()
        confOverrides.setProperty("testpeerinfos", peerList.toTypedArray())

        val testName: String = getTestName()
        for (nodeSetup in sysSetup.nodeMap.values) {
            val nodeConfigProvider = NodeConfigurationProviderGenerator.buildFromSetup(
                testName,
                confOverrides,
                nodeSetup,
                sysSetup,
                setupAction
            )
            nodeSetup.configurationProvider = nodeConfigProvider
        }
    }

    /**
     * Starts the nodes with the number of chains different for each node
     *
     * NOTE: This is a more generic function compared to the createMultiChainNodes function. It handles any setup.
     *
     * @param systemSetup is holds the configuration of all the nodes and chains
     * @return list of [PostchainTestNode] s.
     */
    protected fun createMultiChainNodesFromSystemSetup(systemSetup: SystemSetup) =
        systemSetup.toTestNodes().toTypedArray()

    /**
     * Takes a [SystemSetup] and adds [NodeConfigurationProvider] to all [NodeSetup] in it.
     */
    protected fun addConfigProviderToNodeSetups(
            systemSetup: SystemSetup,
            configOverrides: MapConfiguration,
            setupAction: (appConfig: AppConfig, nodeConfig: NodeConfig) -> Unit = { _, _ -> Unit }
    ) {
        val testName = this::class.simpleName!!
        for (nodeSetup in systemSetup.nodeMap.values) {

            val nodeConfProv: NodeConfigurationProvider = NodeConfigurationProviderGenerator.buildFromSetup(
                    testName,
                    configOverrides,
                    nodeSetup,
                    systemSetup,
                    setupAction)
            nodeSetup.configurationProvider = nodeConfProv // TODO: A bit ugly to mutate an existing instance like this. Ideas?
        }

    }

    fun createPeerInfosWithReplicas(nodeCount: Int, replicasCount: Int): Array<PeerInfo> {
        val pubKeys = Array(nodeCount) { pubKey(it) } + Array(replicasCount) { pubKey(-it - 1) }

        peerInfos = pubKeys.map { Pair(it, ServerSocket(0)) }.map {
            it.second.close()
            it.second.reuseAddress = true
            PeerInfo(it.second.inetAddress.hostName, it.second.localPort, it.first)
        }.toTypedArray()

        return peerInfos!!
    }

    private fun peerInfoFromPublicKey(pubKey: ByteArray): PeerInfo {
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            return PeerInfo(socket.inetAddress.hostName, socket.localPort, pubKey)
        }
    }

    fun createPeerInfosWithReplicas(sysSetup: SystemSetup): Array<PeerInfo> {
        return sysSetup.toPeerInfoList().toTypedArray()
    }

    fun createPeerInfos(nodeCount: Int): Array<PeerInfo> = createPeerInfosWithReplicas(nodeCount, 0)

    protected fun buildBlock(chainId: Long, toHeight: Long, vararg txs: TestTransaction) {
        buildBlock(nodes, chainId, toHeight, *txs)
    }

    protected fun buildBlock(nodes: List<PostchainTestNode>, chainId: Long, toHeight: Long, vararg txs: TestTransaction) {
        buildBlockNoWait(nodes, chainId, toHeight, *txs)
        awaitHeight(nodes, chainId, toHeight)
    }

    protected fun buildBlockNoWait(
        nodes: List<PostchainTestNode>,
        chainId: Long,
        toHeight: Long,
        vararg txs: Transaction
    ) {
        nodes.forEach {
            it.enqueueTxs(chainId, *txs)
        }
        nodes.forEach {
            it.buildBlocksUpTo(chainId, toHeight)
        }
    }

    protected fun awaitHeight(chainId: Long, height: Long) {
        awaitLog("========= AWAIT ALL ${nodes.size} NODES chain:  $chainId, height:  $height (i)")
        awaitHeight(nodes, chainId, height)
        awaitLog("========= DONE AWAIT ALL ${nodes.size} NODES chain: $chainId, height: $height (i)")
    }

    protected fun awaitHeight(nodes: List<PostchainTestNode>, chainId: Long, height: Long) {
        nodes.forEach {
            awaitLog("++++++ AWAIT node RID: ${PeerNameHelper.peerName(it.pubKey)}, chain: $chainId, height: $height (i)")
            it.awaitHeight(chainId, height)
            awaitLog("++++++ WAIT OVER node RID: ${PeerNameHelper.peerName(it.pubKey)}, chain: $chainId, height: $height (i)")
        }
    }

}