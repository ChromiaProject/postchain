// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.reconnection

import assertk.assertThat
import assertk.assertions.isEmpty
import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.assertChainStarted
import org.awaitility.Awaitility.await
import org.awaitility.Duration.FIVE_SECONDS
import org.awaitility.Duration.TEN_SECONDS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnknownPeerConnectionTest : ReconnectionTest() {

    private val nodeConfigs = (0 until 4).map { "classpath:/net/postchain/reconnection/unknown-peers/node$it.properties" }
    private val blockchainConfig = "/net/postchain/devtools/reconnection/blockchain_config_2.xml"

    @Test
    fun `only one unknown peer can connect`() {
        val nodesCount = 2
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        configOverrides.setProperty("connection.max_unknown_peer_connections_per_chain", 1)

        // Creating signer peers
        createSingleNode(0, nodesCount, nodeConfigs[0], blockchainConfig)
        createSingleNode(1, nodesCount, nodeConfigs[1], blockchainConfig)

        // Asserting that chain is started
        await().atMost(FIVE_SECONDS).untilAsserted {
            nodes[0].assertChainStarted()
            nodes[1].assertChainStarted()
        }

        await().atMost(FIVE_SECONDS).untilAsserted {
            assertEquals(nodeRids(1), nodes[0].getConnectedNodes())
            assertEquals(nodeRids(0), nodes[1].getConnectedNodes())
        }

        // unknown peer3 can connect to the network
        createSingleNode(2, nodesCount, nodeConfigs[2], blockchainConfig)
        await().atMost(FIVE_SECONDS).untilAsserted {
            nodes[2].assertChainStarted()
            assertEquals(nodeRids(1, 2), nodes[0].getConnectedNodes())
            assertEquals(nodeRids(0, 2), nodes[1].getConnectedNodes())
            assertEquals(nodeRids(0, 1), nodes[2].getConnectedNodes())
        }

        // but the 2nd unknown peer4 can not connect to the network
        createSingleNode(3, nodesCount, nodeConfigs[3], blockchainConfig)
        await().pollDelay(TEN_SECONDS).atMost(TEN_SECONDS + FIVE_SECONDS).untilAsserted {
            nodes[3].assertChainStarted()
            assertEquals(nodeRids(1, 2), nodes[0].getConnectedNodes())
            assertEquals(nodeRids(0, 2), nodes[1].getConnectedNodes())
            assertEquals(nodeRids(0, 1), nodes[2].getConnectedNodes())
            assertThat(nodes[3].getConnectedNodes()).isEmpty()
        }
    }

    private fun nodeRids(vararg index: Int) = index.map { NodeRid(nodes[it].pubKey.hexStringToByteArray()) }.toSet()

    private fun PostchainTestNode.getConnectedNodes() = postchainContext.connectionManager.getConnectedNodes(1).toSet()
}