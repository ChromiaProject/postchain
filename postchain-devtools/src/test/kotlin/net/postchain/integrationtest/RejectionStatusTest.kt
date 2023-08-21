package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.common.tx.TransactionStatus
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.enqueueTxs
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Test

class RejectionStatusTest : IntegrationTestSetup() {

    @Test
    fun `Rejection status should be cleared when resubmitting tx`() {
        val nodes = createNodes(1, "/net/postchain/devtools/manual/blockchain_config.xml")

        val errorTx = TestTransaction(0, correct = false)
        val txRID = errorTx.getRID()
        buildBlock(1L, 0, errorTx)

        val status = nodes[0].transactionQueue(1L).getTransactionStatus(txRID)
        assertThat(status).isEqualTo(TransactionStatus.REJECTED)

        val correctTx = TestTransaction(0)
        // Sanity check that we have the same tx RID for the correct tx
        assertThat(correctTx.getRID().contentEquals(txRID)).isTrue()

        nodes[0].enqueueTxs(1L, correctTx)
        val resubmittedStatus = nodes[0].transactionQueue(1L).getTransactionStatus(txRID)
        assertThat(resubmittedStatus).isEqualTo(TransactionStatus.WAITING)

        buildBlock(1L, 1)
        val afterBuildStatus = nodes[0].transactionQueue(1L).getTransactionStatus(txRID)
        // Queue now has no info about this tx (have to check DB to see if it was successful)
        assertThat(afterBuildStatus).isEqualTo(TransactionStatus.UNKNOWN)
    }

}