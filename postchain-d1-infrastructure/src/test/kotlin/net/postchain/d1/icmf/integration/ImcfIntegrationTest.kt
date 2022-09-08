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
import net.postchain.gtv.GtvEncoder
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

        buildBlock(CHAIN_ID.toLong(), 0, IcmfTestTransaction(0), IcmfTestTransaction(1))

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
                assertEquals("topic", res1[0]["topic"])

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
                    decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()["topic"]!!.asByteArray()
                )

                val allMessages = IcmfGTXModule.getAllMessages(Unit, it, gtv(mapOf("topic" to gtv("topic"), "height" to gtv(0))))
                assertEquals(2, allMessages.asArray().size)
                assertEquals("test0", allMessages.asArray()[0].asString())
                assertEquals("test1", allMessages.asArray()[1].asString())

                val messages = IcmfGTXModule.getMessages(Unit, it, gtv(mapOf("topic" to gtv("topic"), "height" to gtv(0))))
                assertEquals(2, messages.asArray().size)
                assertEquals("test0", messages.asArray()[0].asString())
                assertEquals("test1", messages.asArray()[1].asString())
            }
        }
    }
}