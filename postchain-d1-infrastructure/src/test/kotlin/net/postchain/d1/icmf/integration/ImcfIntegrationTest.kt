package net.postchain.d1.icmf.integration

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.d1.icmf.IcmfTestTransaction
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.d1.icmf.ICMF_BLOCK_HEADER_EXTRA
import net.postchain.d1.icmf.IcmfGTXModule
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.junit.jupiter.api.Test
import net.postchain.d1.icmf.tableMessages
import net.postchain.devtools.getModules
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

        val op = OpData("test_message", arrayOf())
        val transactor = nodes[0].getModules(CHAIN_ID.toLong()).find { it.javaClass.simpleName.startsWith("Rell") }!!.makeTransactor(
            ExtOpData.build(
                op, 0, GTXTransactionBodyData(nodes[0].getBlockchainRid(CHAIN_ID.toLong())!!, arrayOf(op), arrayOf())
            )
        )

        buildBlock(CHAIN_ID.toLong(), 0, IcmfTestTransaction(0, transactor))

        for (node in nodes) {
            withReadConnection(node.postchainContext.storage, CHAIN_ID.toLong()) {
                val db = DatabaseAccess.of(it)
                val queryRunner = QueryRunner()

                val res1 = queryRunner.query(
                    it.conn,
                    "SELECT block_height, prev_message_block_height, topic FROM ${db.tableMessages(it)}",
                    MapListHandler()
                )
                assertEquals(1, res1.size)
                assertEquals(0L, res1[0]["block_height"])
                assertEquals(-1L, res1[0]["prev_message_block_height"])
                assertEquals("my-topic", res1[0]["topic"])

                val blockQueries = node.getBlockchainInstance(CHAIN_ID.toLong()).blockchainEngine.getBlockQueries()
                val blockRid = blockQueries.getBlockRid(0).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderDataFactory.buildFromBinary(blockHeader.rawData)
                val expectedHash = cryptoSystem.digest(
                    cryptoSystem.digest(GtvEncoder.encodeGtv(gtv("hej"))))

                assertContentEquals(
                    expectedHash,
                    decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()["my-topic"]!!.asByteArray()
                )

                val allMessages =
                    IcmfGTXModule.getAllMessages(Unit, it, gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(1, allMessages.asArray().size)
                assertEquals("hej", allMessages.asArray()[0].asString())

                val messages =
                    IcmfGTXModule.getMessages(Unit, it, gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(1, messages.asArray().size)
                assertEquals("hej", messages.asArray()[0].asString())
            }
        }
    }
}
