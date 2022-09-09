package net.postchain.d1.icmf.integration

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.ICMF_BLOCK_HEADER_EXTRA
import net.postchain.d1.icmf.IcmfGTXModule
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.junit.jupiter.api.Test
import net.postchain.d1.icmf.tableMessages
import net.postchain.devtools.getModules
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvEncoder
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.data.GTXTransactionBodyData
import net.postchain.gtx.data.OpData
import org.apache.commons.dbutils.handlers.MapListHandler
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val CHAIN_ID = 1

class ImcfIntegrationTest : GtxTxIntegrationTestSetup() {

    @Test
    fun icmfHappyPath() {
        val mapBcFiles: Map<Int, String> = mapOf(
            CHAIN_ID to "/net/postchain/icmf/integration/blockchain_config_1.xml",
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        runXNodes(sysSetup)

        buildBlock(
            CHAIN_ID.toLong(), 0, makeTransaction(0, OpData("test_message", arrayOf(gtv("test0")))),
            makeTransaction(1, OpData("test_message", arrayOf(gtv("test1"))))
        )

        for (node in nodes) {
            withReadConnection(node.postchainContext.storage, CHAIN_ID.toLong()) {
                val db = DatabaseAccess.of(it)
                val queryRunner = QueryRunner()

                val res1 = queryRunner.query(
                    it.conn,
                    "SELECT block_height, prev_message_block_height, topic FROM ${db.tableMessages(it)}",
                    MapListHandler()
                )
                assertEquals(2, res1.size)
                assertEquals(0L, res1[0]["block_height"])
                assertEquals(-1L, res1[0]["prev_message_block_height"])
                assertEquals("my-topic", res1[0]["topic"])

                val blockQueries = node.getBlockchainInstance(CHAIN_ID.toLong()).blockchainEngine.getBlockQueries()
                val blockRid = blockQueries.getBlockRid(0).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderDataFactory.buildFromBinary(blockHeader.rawData)
                val expectedHash = cryptoSystem.digest(
                    cryptoSystem.digest(GtvEncoder.encodeGtv(gtv("test0")))
                            + cryptoSystem.digest(
                        GtvEncoder.encodeGtv(gtv("test1"))
                    )
                )
                assertContentEquals(
                    expectedHash,
                    decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()["my-topic"]!!.asByteArray()
                )

                val allMessages =
                    IcmfGTXModule.getAllMessages(Unit, it, gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(2, allMessages.asArray().size)
                assertEquals("test0", allMessages.asArray()[0].asString())
                assertEquals("test1", allMessages.asArray()[1].asString())

                val messages =
                    IcmfGTXModule.getMessages(Unit, it, gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(2, messages.asArray().size)
                assertEquals("test0", messages.asArray()[0].asString())
                assertEquals("test1", messages.asArray()[1].asString())
            }
        }
    }

    private fun makeTransaction(id: Int, op: OpData) =
        IcmfTestTransaction(
            id,
            nodes[0].getModules(CHAIN_ID.toLong()).find { it.javaClass.simpleName.startsWith("Rell") }!!.makeTransactor(
                ExtOpData.build(
                    op,
                    0,
                    GTXTransactionBodyData(nodes[0].getBlockchainRid(CHAIN_ID.toLong())!!, arrayOf(op), arrayOf())
                )
            )
        )

    class IcmfTestTransaction(id: Int, val op: Transactor, good: Boolean = true, correct: Boolean = true) :
        TestTransaction(id, good, correct) {
        override fun apply(ctx: TxEContext): Boolean {
            op.isCorrect()
            op.apply(ctx)
            return true
        }
    }
}
