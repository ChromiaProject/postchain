package net.postchain.integrationtest.snapshot

import mu.KLogging
import net.postchain.devtools.IntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.testinfra.TestTransaction
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

    protected fun tx(id: Int): TestTransaction = TestTransaction(id)

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
            createSingleNode(i, nodesCount, nodeConfig, "/net/postchain/devtools/snapshot/blockchain_config_3.xml")
        }

        // Asserting chain 1 is started for all nodes
        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes.forEach { it.assertChainStarted() }
                }


        nodes[0].enqueueTxsAndAwaitBuiltBlock(PostchainTestNode.DEFAULT_CHAIN_IID, 6, tx(0), tx(1))
    }
}