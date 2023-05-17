// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import mu.KLogging
import net.postchain.concurrent.util.get
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals

open class FullEbftTestNightlyCore : ConfigFileBasedIntegrationTest() {

    companion object : KLogging()

    protected fun runXNodesWithYTxPerBlockTest(blocksCount: Int, txPerBlock: Int) {
        var txId = 0
        for (i in 0 until blocksCount) {
            for (tx in 0 until txPerBlock) {
                val currentTxId = txId++
                nodes.forEach {
                    it.getBlockchainInstance()
                            .blockchainEngine
                            .getTransactionQueue()
                            .enqueue(TestTransaction(currentTxId))
                }
            }
            logger.info { "Trigger block" }
            buildBlock(i)
        }

        val queries = nodes[0].getBlockchainInstance().blockchainEngine.getBlockQueries()
        val referenceHeight = queries.getLastBlockHeight().get()
        logger.info { "$blocksCount, refHe: $referenceHeight" }
        nodes.forEach { node ->
            val blockQueries = node.getBlockchainInstance().blockchainEngine.getBlockQueries()
            assertEquals(referenceHeight, queries.getLastBlockHeight().get())

            for (height in 0..referenceHeight) {
                logger.info { "Verifying height $height" }
                val rid = blockQueries.getBlockRid(height).get()
                requireNotNull(rid)

                val txs = blockQueries.getBlockTransactionRids(rid).get()
                assertEquals(txPerBlock, txs.size)

                for (tx in 0 until txPerBlock) {
                    val expectedTx = TestTransaction((height * txPerBlock + tx).toInt())
                    assertArrayEquals(expectedTx.getRID(), txs[tx])

                    val actualTx = blockQueries.getTransaction(txs[tx]).get()
                    assertArrayEquals(expectedTx.getRID(), actualTx?.getRID())
                    assertArrayEquals(expectedTx.getRawData(), actualTx!!.getRawData())
                }
            }
        }
    }
}
