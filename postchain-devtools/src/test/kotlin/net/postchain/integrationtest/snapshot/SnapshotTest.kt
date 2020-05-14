package net.postchain.integrationtest.snapshot

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.devtools.IntegrationTest
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXDataBuilder
import net.postchain.integrationtest.assertChainStarted
import net.postchain.integrationtest.enqueueTxsAndAwaitBuiltBlock
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Paths

open class SnapshotTest: IntegrationTest() {

    private var snapshotFolder: String = "snapshot"

    companion object : KLogging()

    private fun strategy(node: PostchainTestNode): OnDemandBlockBuildingStrategy {
        return node
                .getBlockchainInstance()
                .getEngine()
                .getBlockBuildingStrategy() as OnDemandBlockBuildingStrategy
    }

    protected fun tx(id: Int): TestTransaction = TestTransaction(id)

    private fun makeTx(ownerIdx: Int, key: Long, value: String, bcRid: BlockchainRid): ByteArray {
        val owner = KeyPairHelper.pubKey(ownerIdx)
        return GTXDataBuilder(bcRid, arrayOf(owner), net.postchain.devtools.gtx.myCS).run {
            addOperation("gtx_test", arrayOf(GtvFactory.gtv(key), GtvFactory.gtv(value)))
            finish()
            sign(net.postchain.devtools.gtx.myCS.buildSigMaker(owner, KeyPairHelper.privKey(ownerIdx)))
            serialize()
        }
    }

    @After
    override fun tearDown() {
        logger.debug("Integration test -- TEARDOWN")
        nodes.forEach { it.shutdown() }
        nodes.clear()
        nodesNames.clear()
        logger.debug("Closed nodes")
        peerInfos = null
        expectedSuccessRids = mutableMapOf()
        configOverrides.clear()
        val path = Paths.get("").toAbsolutePath().normalize().toString() + File.separator + snapshotFolder
        val file = File(path)
        file.deleteRecursively()
        logger.debug("Deleted snapshot folder")
        System.gc()
    }

    @Test
    fun snapshotBuilding_SingleNode() {
        val nodesCount = 1
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        val nodeConfigsFilenames = arrayOf(
                "classpath:/net/postchain/snapshot/node0.properties"
        )

        // Creating all nodes
        nodeConfigsFilenames.forEachIndexed { i, nodeConfig ->
            createSingleNode(i, nodesCount, nodeConfig, "/net/postchain/devtools/snapshot/blockchain_config.xml")
        }

        // Asserting chain 1 is started for all nodes
        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes.forEach { it.assertChainStarted() }
                }


        // Building a blocks up to height 100 to trigger snapshot building worker to run several times
        nodes[0].enqueueTxsAndAwaitBuiltBlock(PostchainTestNode.DEFAULT_CHAIN_IID, 100, tx(0), tx(1))
    }

    @Test
    fun snapshotBuilding_SingleNode_WithRellApp() {
        val nodesCount = 1
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))

        // Creating a node
        val node = createSingleNode(0, nodesCount, "classpath:/net/postchain/snapshot/node0.properties", "/net/postchain/devtools/snapshot/blockchain_config_2.xml")
        val bcRid = node.getBlockchainRid(1L)!!

        // Asserting chain 1 is started for all nodes
        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes.forEach { it.assertChainStarted() }
                }

        val engine = node.getBlockchainInstance().getEngine()
        val txFactory = engine.getConfiguration().getTransactionFactory()
        val queue = engine.getTransactionQueue()
        val txs = (1..10).map { makeTx(0, 1L, "snapshot$it", bcRid) }
        txs.forEach {
            queue.enqueue(txFactory.decodeTransaction(it))
        }
        strategy(node).buildBlocksUpTo(10L)
        strategy(node).awaitCommitted(10)

        // Building a blocks up to height 100 to trigger snapshot building worker to run several times
        node.enqueueTxsAndAwaitBuiltBlock(PostchainTestNode.DEFAULT_CHAIN_IID, 100, tx(0), tx(1))
    }

    @Test
    fun snapshotBuilding_MultipleNodes() {
        snapshotFolder = "postchain"
        val nodesCount = 3
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        val nodeConfigsFilenames = arrayOf(
                "classpath:/net/postchain/snapshot/node0.properties",
                "classpath:/net/postchain/snapshot/node1.properties",
                "classpath:/net/postchain/snapshot/node2.properties"
        )

        // Creating all nodes
        nodeConfigsFilenames.forEachIndexed { i, nodeConfig ->
            createSingleNode(i, nodesCount, nodeConfig, "/net/postchain/devtools/snapshot/blockchain_config_3.xml", preWipeDatabase = true)
        }

        // Asserting chain 1 is started for all nodes
        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes.forEach { it.assertChainStarted() }
                }


        nodes[0].enqueueTxsAndAwaitBuiltBlock(PostchainTestNode.DEFAULT_CHAIN_IID, 6, tx(0), tx(1))
    }
}