package net.postchain.d1.icmf.integration

import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.ICMF_BLOCK_HEADER_EXTRA
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val CHAIN_ID = 1

class ImcfSenderIntegrationTest : GtxTxIntegrationTestSetup() {

    @Test
    fun icmfHappyPath() {
        val mapBcFiles: Map<Int, String> = mapOf(
                CHAIN_ID to "/net/postchain/icmf/integration/blockchain_config_1.xml",
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        runXNodes(sysSetup)

        buildBlock(
                CHAIN_ID.toLong(), 0, makeTransaction(nodes[0], 0, GtxOp("test_message", gtv("test0"))),
                makeTransaction(nodes[0], 1, GtxOp("test_message", gtv("test1")))
        )

        for (node in nodes) {
            withReadConnection(node.postchainContext.storage, CHAIN_ID.toLong()) {
                val blockQueries = node.getBlockchainInstance(CHAIN_ID.toLong()).blockchainEngine.getBlockQueries()
                val blockRid = blockQueries.getBlockRid(0).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderData.fromBinary(blockHeader.rawData)
                val expectedHash = cryptoSystem.digest(
                        cryptoSystem.digest(GtvEncoder.encodeGtv(gtv("test0")))
                                + cryptoSystem.digest(
                                GtvEncoder.encodeGtv(gtv("test1"))
                        )
                )
                val topicHeader = decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()["my-topic"]!!.asDict()
                assertContentEquals(
                        expectedHash,
                        topicHeader["hash"]!!.asByteArray()
                )
                // TODO test other values for previous height
                assertEquals(-1, topicHeader["prev_message_block_height"]!!.asInteger())

                val allMessages =
                        query(node, it, "icmf_get_all_messages", gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(2, allMessages.asArray().size)
                assertEquals("test0", allMessages.asArray()[0].asString())
                assertEquals("test1", allMessages.asArray()[1].asString())

                val messages =
                        query(node, it, "icmf_get_messages", gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(2, messages.asArray().size)
                assertEquals("test0", messages.asArray()[0].asString())
                assertEquals("test1", messages.asArray()[1].asString())
            }
        }
    }

    private fun query(node: PostchainTestNode, ctxt: EContext, name: String, args: Gtv): Gtv =
            node.getModules(CHAIN_ID.toLong()).find { it.javaClass.simpleName.startsWith("Rell") }!!.query(ctxt, name, args)

    private fun makeTransaction(node: PostchainTestNode, id: Int, op: GtxOp) =
            IcmfTestTransaction(
                    id,
                    node.getModules(CHAIN_ID.toLong()).find { it.javaClass.simpleName.startsWith("Rell") }!!.makeTransactor(
                            ExtOpData.build(
                                    op,
                                    0,
                                    GtxBody(node.getBlockchainRid(CHAIN_ID.toLong())!!, arrayOf(op), arrayOf())
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
