package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.base.withReadWriteConnection
import net.postchain.common.exception.TransactionFailed
import net.postchain.common.exception.TransactionTimeout
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.testinfra.DelayedTransaction
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LongRunningTransactionSlowIntegrationTest : IntegrationTestSetup() {

    @Test
    fun `Long running tx should be accepted eventually`() {
        val nodes = createNodes(3, "/net/postchain/devtools/long_running/blockchain_config.xml")

        val delayedTx = DelayedTransaction(0, 11_000)
        buildBlock(1L, 0, delayedTx)
        nodes.forEach {
            val txsInBlock = getTxRidsAtHeight(it, 0)
            assertThat(txsInBlock.size).isEqualTo(1)
            assertThat(txsInBlock[0]).isContentEqualTo(delayedTx.getRID())
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
            assertThat(txsInBlock.size).isEqualTo(2)
            assertThat(txsInBlock[0]).isContentEqualTo(firstTx.getRID())
            assertThat(txsInBlock[1]).isContentEqualTo(lastTx.getRID())

            val transactionQueue = node.transactionQueue(1L)
            assertThat(transactionQueue.getTransactionStatus(delayedTx.getRID()))
                    .isEqualTo(TransactionStatus.REJECTED)
            assertThat(transactionQueue.getRejectionReason(delayedTx.getRID().wrap())?.message)
                    .isEqualTo("Transaction ${delayedTx.getRID().toHex()} failed to execute within given time constraint: 10000 ms")
        }
    }

    @Test
    fun `Long running tx should not be rejected when syncing`() {
        val (node) = createNodes(1, "/net/postchain/devtools/long_running/blockchain_config_max_execution_time_sync.xml")

        val delayedTx = DelayedTransaction(0, 1500)
        withReadWriteConnection(node.postchainContext.blockBuilderStorage, 1L) {
            // not syncing, should respect timeout
            val nonSyncBB = node.getBlockchainInstance().blockchainEngine.getConfiguration().makeBlockBuilder(it, false)
            nonSyncBB.begin(null)
            assertThrows<TransactionTimeout> {
                nonSyncBB.appendTransaction(delayedTx)
            }
            it.conn.rollback()

            // syncing, ignoring timeout
            val syncBB = node.getBlockchainInstance().blockchainEngine.getConfiguration().makeBlockBuilder(it, true)
            syncBB.begin(null)
            syncBB.appendTransaction(delayedTx)

            syncBB.finalizeBlock()
            assertThat(syncBB.getBlockData().transactions.first()).isContentEqualTo(delayedTx.getRawData())
        }
    }
}