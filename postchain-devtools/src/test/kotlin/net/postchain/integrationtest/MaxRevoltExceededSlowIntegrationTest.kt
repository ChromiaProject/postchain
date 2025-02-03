package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.concurrent.util.get
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test

class MaxRevoltExceededSlowIntegrationTest : IntegrationTestSetup() {

    @Test
    fun `Last taken tx should be evicted when block building time exceeds max revolt time`() {
        val nodes = createNodes(2, "/net/postchain/devtools/max_revolt_exceeded/blockchain_config.xml")
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid

        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()
        val normalTx = transactionFactory.decodeTransaction(buildDelayedOpEventTx(brid, 0))
        val delayedTx = transactionFactory.decodeTransaction(buildDelayedOpEventTx(brid, 2_000))
        buildBlock(DEFAULT_CHAIN_IID, 0L, normalTx, delayedTx)

        val blockQueries = nodes[0].getBlockchainInstance().blockchainEngine.getBlockQueries()
        val normalTxInfo = blockQueries.getTransactionInfo(normalTx.getRID(), includeTxData = true)
                .get()
        assertThat(normalTxInfo).isNotNull()
        assertThat(normalTxInfo!!.blockHeight).isEqualTo(0)

        val transactionQueue = nodes[0].getBlockchainInstance().blockchainEngine.getTransactionQueue()
        assertThat(transactionQueue.getTransactionStatus(delayedTx.getRID()))
                .isEqualTo(TransactionStatus.UNKNOWN)
        val delayedTxInfo = blockQueries.getTransactionInfo(delayedTx.getRID(), includeTxData = true)
                .get()
        assertThat(delayedTxInfo).isNull()
    }

    private fun buildDelayedOpEventTx(brid: BlockchainRid, delay: Long) = GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
            .addOperation("delayed_op", gtv(delay))
            .addNop()
            .finish()
            .buildGtx()
            .encode()
}

class DelayedOpTestGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(
        "delayed_op" to { _, opData -> DelayedOp(opData) }
), mapOf()) {
    override fun initializeDB(ctx: EContext) {}
}

class DelayedOp(private val opData: ExtOpData) : GTXOperation(opData) {

    override fun checkCorrectness() {
        if (data.args.size != 1) throw UserMistake("data.args.size != 1")
    }

    override fun apply(ctx: TxEContext): Boolean {
        Thread.sleep(opData.args[0].asInteger())
        return true
    }
}
