package net.postchain.integrationtest.reconnection

import net.postchain.integrationtest.assertChainStarted
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.Before
import org.junit.Test

class FourPeersReconnectionTest : FourPeersReconnectionImpl() {

    @Before
    fun setUp() {
        reset()
    }

    @Test
    fun test4Peers() {
        val nodesCount = 4
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        val blockchainConfig = "/net/postchain/devtools/reconnection/blockchain_config_4.xml"
        val nodeConfigsFilenames = arrayOf(
                "classpath:/net/postchain/reconnection/node0.properties",
                "classpath:/net/postchain/reconnection/node1.properties",
                "classpath:/net/postchain/reconnection/node2.properties",
                "classpath:/net/postchain/reconnection/node3.properties"
        )

        // Creating all peers
        nodeConfigsFilenames.forEachIndexed { i, nodeConfig ->
            createSingleNode(i, nodesCount, nodeConfig, blockchainConfig)
        }

        // Asserting that chain is started
        await().atMost(Duration.FIVE_SECONDS)
                .untilAsserted {
                    nodes.forEach { it.assertChainStarted() }
                }

        // Asserting height is -1 for all peers
        assertHeightForAllNodes(-1)

        // Building 6 blocks
        buildNotEmptyBlocks(6, randNode())
        // Asserting height is 5 for all peers
        assertHeightForAllNodes(5)

        // Shutting down node 3
        nodes[3].shutdown()

        // Asserting that node3 is stopped
        assertChainStarted(true, true, true, false)
        assertTopology(0, 1, 2)

        // Removing peer 3
        nodes.removeAt(3)

        // Building additional 6 blocks
        buildNotEmptyBlocks(6, randNode3())

        println("Stating peer 3 ...")
        createSingleNode(3, nodesCount, nodeConfigsFilenames[1], blockchainConfig)

        // Asserting that node3 is a part of network
        assertChainStarted(true, true, true, true)
        assertTopology(0, 1, 2, 3)

        // Building additional 6 blocks
        buildNotEmptyBlocks(6, randNode())
        // Asserting height is "6 + 6 + 6 - 1" for all peers
        assertHeightForAllNodes(6 + 6 + 6 - 1)

        // And building additional 6 blocks via peer 3
        buildNotEmptyBlocks(6, randNode())
        // Asserting height is "6 + 6 + 6 + 6 - 1" for all peers
        assertHeightForAllNodes(6 + 6 + 6 + 6 - 1)
    }
}