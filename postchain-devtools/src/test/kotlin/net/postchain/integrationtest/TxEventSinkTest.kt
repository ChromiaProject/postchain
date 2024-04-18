package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.concurrent.util.get
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import net.postchain.integrationtest.TxEventSinkTestBlockBuilderExtension.Companion.FAILURE_EVENT
import net.postchain.integrationtest.TxEventSinkTestBlockBuilderExtension.Companion.SUCCESS_EVENT
import net.postchain.integrationtest.TxEventSinkTestBlockBuilderExtension.Companion.SUCCESS_EVENTS_HEADER
import org.junit.jupiter.api.Test

class TxEventSinkTest : IntegrationTestSetup() {

    @Test
    fun `tx event sink should not apply changes unless tx is successfully appended`() {
        val nodes = createNodes(1, "/net/postchain/devtools/tx_event_sink/blockchain_config.xml")

        val brid = BlockchainRid("CB01AAF54B3AEA7E7215179AA8986800AF311DF413BC09CF5AE02C969287BDFF".hexStringToByteArray())

        val successEventTx = nodes[0].getBlockchainInstance(1).blockchainEngine.getConfiguration().getTransactionFactory()
                .decodeTransaction(buildSuccessEventTx(brid))
        buildBlock(1, successEventTx)

        // Assert event was recorded in header
        val block = nodes[0].getBlockchainInstance(1).blockchainEngine.getBlockQueries().getBlockAtHeight(0)
                .get()!!
        assertThat(BlockHeaderData.fromBinary(block.header.rawData).gtvExtra[SUCCESS_EVENTS_HEADER]!!.asInteger())
                .isEqualTo(1)

        val successAndFailureEventTx = nodes[0].getBlockchainInstance(1).blockchainEngine.getConfiguration().getTransactionFactory()
                .decodeTransaction(buildSuccessAndFailureEventTx(brid))
        buildBlock(1, successAndFailureEventTx)

        // Assert failing tx did not save any event in header
        val block2 = nodes[0].getBlockchainInstance(1).blockchainEngine.getBlockQueries().getBlockAtHeight(1)
                .get()!!
        assertThat(BlockHeaderData.fromBinary(block2.header.rawData).gtvExtra[SUCCESS_EVENTS_HEADER]!!.asInteger())
                .isEqualTo(0)

        val successEventTx2 = nodes[0].getBlockchainInstance(1).blockchainEngine.getConfiguration().getTransactionFactory()
                .decodeTransaction(buildSuccessEventTx(brid))
        val failureEventTx = nodes[0].getBlockchainInstance(1).blockchainEngine.getConfiguration().getTransactionFactory()
                .decodeTransaction(buildFailureEventTx(brid))
        buildBlock(1, successEventTx2, failureEventTx)

        // Assert successful tx did save event in header even though another tx failed
        val block3 = nodes[0].getBlockchainInstance(1).blockchainEngine.getBlockQueries().getBlockAtHeight(2)
                .get()!!
        assertThat(BlockHeaderData.fromBinary(block3.header.rawData).gtvExtra[SUCCESS_EVENTS_HEADER]!!.asInteger())
                .isEqualTo(1)
    }

    private fun buildSuccessEventTx(brid: BlockchainRid) = GtxBuilder(brid, emptyList(), cryptoSystem)
            .addOperation("emit_test_event", gtv(SUCCESS_EVENT))
            .addNop()
            .finish()
            .buildGtx()
            .encode()

    private fun buildFailureEventTx(brid: BlockchainRid) = GtxBuilder(brid, emptyList(), cryptoSystem)
            .addOperation("emit_test_event", gtv(FAILURE_EVENT))
            .addNop()
            .finish()
            .buildGtx()
            .encode()

    private fun buildSuccessAndFailureEventTx(brid: BlockchainRid) = GtxBuilder(brid, emptyList(), cryptoSystem)
            .addOperation("emit_test_event", gtv(SUCCESS_EVENT))
            .addOperation("emit_test_event", gtv(FAILURE_EVENT))
            .addNop()
            .finish()
            .buildGtx()
            .encode()
}

class TxEventSinkTestGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(
        "emit_test_event" to { _, opData -> EventOp(opData) }
), mapOf()) {
    override fun initializeDB(ctx: EContext) {}

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(TxEventSinkTestBlockBuilderExtension())
    }
}

class EventOp(private val opData: ExtOpData) : GTXOperation(opData) {

    override fun checkCorrectness() {
        if (data.args.size != 1) throw UserMistake("data.args.size != 1")
    }

    override fun apply(ctx: TxEContext): Boolean {
        ctx.emitEvent(opData.args[0].asString(), GtvNull)
        return true
    }
}

class TxEventSinkTestBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {

    private var successFulEvents = 0L

    companion object {
        const val SUCCESS_EVENT = "success"
        const val FAILURE_EVENT = "failure"

        const val SUCCESS_EVENTS_HEADER = "success_events"
    }

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        baseBB.installEventProcessor(SUCCESS_EVENT, this)
        baseBB.installEventProcessor(FAILURE_EVENT, this)
    }

    override fun finalize() = mapOf(SUCCESS_EVENTS_HEADER to gtv(successFulEvents))

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        when (type) {
            SUCCESS_EVENT -> ctxt.addAfterAppendHook { successFulEvents++ }
            FAILURE_EVENT -> throw Exception("You wanted me to fail")
        }
    }

}
