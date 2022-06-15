package net.postchain.integrationtest

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.common.data.byteArrayKeyOf
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.testinfra.DelayedTransaction
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Test

class LongRunningTransactionTestNightly : IntegrationTestSetup() {

    @Test
    fun `Long running tx should be accepted eventually`() {
        val nodes = createNodes(3, "/net/postchain/devtools/long_running/blockchain_config.xml")

        val delayedTx = DelayedTransaction(0, 11_000)
        buildBlock(1L, 0, delayedTx)
        nodes.forEach {
            val txsInBlock = getTxRidsAtHeight(it, 0)
            assert(txsInBlock.size).isEqualTo(1)
            assert(txsInBlock[0]).isContentEqualTo(delayedTx.getRID())
        }
    }

    @Test
    fun `Long running tx should be rejected when exceeding timeout`() {
        val nodes = createNodes(3, "/net/postchain/devtools/long_running/blockchain_config_max_execution_time.xml")

        val firstTx = TestTransaction(0)
        val delayedTx = DelayedTransaction(1, 11_000)
        val lastTx = TestTransaction(2)
        // Build 3 blocks so that each node can reject the delayed tx
        buildBlock(1L, 2, firstTx, delayedTx, lastTx)
        nodes.forEach { node ->
            // Sorting the txs so we can do our assertions safely
            val txsInBlock = getTxRidsAtHeight(node, 0).apply { sortBy { it.toHex() } }
            assert(txsInBlock.size).isEqualTo(2)
            assert(txsInBlock[0]).isContentEqualTo(firstTx.getRID())
            assert(txsInBlock[1]).isContentEqualTo(lastTx.getRID())

            val transactionQueue = node.transactionQueue(1L)
            assert(transactionQueue.getTransactionStatus(delayedTx.getRID()))
                    .isEqualTo(TransactionStatus.REJECTED)
            assert(transactionQueue.getRejectionReason(delayedTx.getRID().byteArrayKeyOf())?.message)
                    .isEqualTo("Transaction failed to execute within given time constraint: 10000 ms")
        }
    }
}