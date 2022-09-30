package net.postchain.d1.icmf

import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IcmfSenderIT : ManagedModeTest() {

    @Test
    fun icmfHappyPath() {
        startManagedSystem(3, 0)

        val rellCode = File("postchain-d1-infrastructure/src/main/rell/icmf/module.rell").readText() +
                """
                    operation test_message(text) {
                        send_message("my-topic", text.to_gtv());
                    }
                """
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/sender/blockchain_config_1.xml")!!.readText(),
                mapOf("rell" to gtv(rellCode)))

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = IcmfTestBlockchainConfigurationFactory())

        buildBlock(
                dappChain.chain, 0, makeTransaction(dappChain.nodes()[0], dappChain.chain, 0, GtxOp("test_message", gtv("test0"))),
                makeTransaction(dappChain.nodes()[0], dappChain.chain, 1, GtxOp("test_message", gtv("test1")))
        )

        for (node in dappChain.nodes()) {
            withReadConnection(node.postchainContext.storage, dappChain.chain) {
                val blockQueries = node.getBlockchainInstance(dappChain.chain).blockchainEngine.getBlockQueries()
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
                        query(node, it, dappChain.chain, "icmf_get_all_messages", gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(2, allMessages.asArray().size)
                assertEquals("test0", allMessages.asArray()[0].asString())
                assertEquals("test1", allMessages.asArray()[1].asString())

                val messages =
                        query(node, it, dappChain.chain, "icmf_get_messages", gtv(mapOf("topic" to gtv("my-topic"), "height" to gtv(0))))
                assertEquals(2, messages.asArray().size)
                assertEquals("test0", messages.asArray()[0].asString())
                assertEquals("test1", messages.asArray()[1].asString())
            }
        }
    }

    private fun query(node: PostchainTestNode, ctxt: EContext, chainId: Long, name: String, args: Gtv): Gtv =
            node.getModules(chainId).find { it.javaClass.simpleName.startsWith("Rell") }!!.query(ctxt, name, args)

    private fun makeTransaction(node: PostchainTestNode, chainId: Long, id: Int, op: GtxOp) =
            IcmfTestTransaction(
                    id,
                    node.getModules(chainId).find { it.javaClass.simpleName.startsWith("Rell") }!!.makeTransactor(
                            ExtOpData.build(
                                    op,
                                    0,
                                    GtxBody(ChainUtil.ridOf(chainId), arrayOf(op), arrayOf())
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
