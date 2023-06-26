// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import net.postchain.api.rest.controller.Model
import net.postchain.concurrent.util.get
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class ThreeTxForwardingTest : IntegrationTestSetup() {

    private fun strat(node: PostchainTestNode): ThreeTxStrategy {
        return node
                .getBlockchainInstance()
                .blockchainEngine
                .getBlockBuildingStrategy() as ThreeTxStrategy
    }

    private fun tx(id: Int): ByteArray = TestTransaction(id).getRawData()

    private fun apiModel(nodeIndex: Int): Model =
            nodes[nodeIndex].getRestApiModel()

    @Test
    @Timeout(2, unit = TimeUnit.MINUTES)
    fun testTxNotForwardedIfPrimary() {
        val count = 3
        configOverrides.setProperty("testpeerinfos", createPeerInfos(count))
        configOverrides.setProperty("api.port", 0)
        createNodes(count, "/net/postchain/devtools/three_tx/blockchain_config.xml")

        apiModel(0).postTransaction(tx(0))
        apiModel(1).postTransaction(tx(1))
        apiModel(2).postTransaction(tx(2))
        strat(nodes[2]).awaitCommitted(0)

        apiModel(0).postTransaction(tx(3))
        apiModel(1).postTransaction(tx(4))
        apiModel(2).postTransaction(tx(5))
        strat(nodes[2]).awaitCommitted(1)

        apiModel(0).postTransaction(tx(6))
        apiModel(1).postTransaction(tx(7))
        apiModel(2).postTransaction(tx(8))
        strat(nodes[2]).awaitCommitted(2)

        val bockQueries = nodes[2].getBlockchainInstance().blockchainEngine.getBlockQueries()
        for (i in 0..2) {
            val blockData = bockQueries.getBlockAtHeight(i.toLong()).get()!!
            assertEquals(3, blockData.transactions.size)
        }
    }
}