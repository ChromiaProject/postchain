// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.utils.configuration.*
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import org.apache.commons.configuration2.MapConfiguration
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import java.lang.IllegalArgumentException

/**
 * This class uses the [SystemSetup] helper class to construct tests, and this way skips node config files, but
 * may or may not use blockchain config files.
 *
 * If you need to provide unusual configurations in your config files, you can use [ConfigFileBasedIntegrationTest]
 * instead (but usually we can sneak in settings via [configOverrides] etc  even here, so it SHOULDN'T be needed)
 */
open class IntegrationTest: AbstractIntegration() {

    protected lateinit var systemSetup: SystemSetup
    protected val nodes = mutableListOf<PostchainTestNode>()
    protected val nodeMap = mutableMapOf<NodeSeqNumber, PostchainTestNode>()
    val configOverrides = MapConfiguration(mutableMapOf<String, String>())

    // PeerInfos must be shared between all nodes because
    // a listening node will update the PeerInfo port after
    // ServerSocket is created.
    private var peerInfos: Array<PeerInfo>? = null
    private var expectedSuccessRids = mutableMapOf<Long, MutableList<ByteArray>>()

    companion object : KLogging() {
        const val BASE_PORT = 9870
    }

    @After
    open fun tearDown() {
        logger.debug("Integration test -- TEARDOWN")
        nodes.forEach { it.shutdown() }
        nodes.clear()
        nodeMap.clear()
        logger.debug("Closed nodes")
        peerInfos = null
        expectedSuccessRids = mutableMapOf()
        configOverrides.clear()
        TestBlockchainRidCache.clear()

        System.gc()
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

        // 3. Create the configuraton provider
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
        val peerList = systemSetup.toPeerInfoList()
        configOverrides.setProperty("testpeerinfos", peerList.toTypedArray())

        val testName: String = this::class.java.simpleName ?: "NoName"   // Get subclass name or dummy
        for (nodeSetup in systemSetup.nodeMap.values) {
            val nodeConfigProvider = NodeConfigurationProviderGenerator.buildFromSetup(testName, configOverrides, nodeSetup, systemSetup, setupAction)
            nodeSetup.configurationProvider = nodeConfigProvider
            val newPTNode = nodeSetup.toTestNodeAndStartAllChains(systemSetup, preWipeDatabase)

            // TODO: not nice to mutate the "nodes" object like this, should return the list of PTNodes instead for testability
            nodes.add(newPTNode)
            nodeMap[nodeSetup.sequenceNumber] = newPTNode
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
    protected fun createMultiChainNodesFromSystemSetup(systemSetup: SystemSetup) =  systemSetup.toTestNodes().toTypedArray()

    /**
     * Takes a [SystemSetup] and adds [NodeConfigurationProvider] to all [NodeSetup] in it.
     */
    protected fun addConfigProviderToNodeSetups(
            systemSetup: SystemSetup,
            configOverrides: MapConfiguration,
            setupAction: (appConfig: AppConfig, nodeConfig: NodeConfig) -> Unit = { _, _ -> Unit }
    )  {
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
        if (peerInfos == null) {
            peerInfos =
                    Array(nodeCount) { PeerInfo("localhost", BASE_PORT + it, pubKey(it)) } +
                            Array(replicasCount) { PeerInfo("localhost", BASE_PORT - it - 1, pubKey(-it - 1)) }
        }

        return peerInfos!!
    }

    fun createPeerInfosWithReplicas(sysSetup: SystemSetup): Array<PeerInfo> {
        return sysSetup.toPeerInfoList().toTypedArray()
    }

    fun createPeerInfos(nodeCount: Int): Array<PeerInfo> = createPeerInfosWithReplicas(nodeCount, 0)



}