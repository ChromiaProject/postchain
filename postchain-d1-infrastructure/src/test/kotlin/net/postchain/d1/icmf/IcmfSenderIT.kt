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

        val rellCode = File("src/main/rell/icmf/module.rell").readText() +
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

        // Messages in block 0
        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.mapIndexed { index, message ->
            makeTransaction(dappChain.nodes()[0], dappChain.chain, index, GtxOp("test_message", gtv(message)))
        }
        buildBlock(dappChain.chain, 0, *block0Txs.toTypedArray())

        verifyMessages(dappChain, 0, "my-topic", -1, block0Messages, block0Messages)

        // No messages in block 1
        buildBlock(dappChain, 1)

        // Messages in block 2
        val block2Messages = listOf("test2", "test3")
        val block2Txs = block2Messages.mapIndexed { index, message ->
            makeTransaction(dappChain.nodes()[0], dappChain.chain, block0Messages.size + index, GtxOp("test_message", gtv(message)))
        }
        buildBlock(dappChain.chain, 2, *block2Txs.toTypedArray())

        // Expecting previous height to be 0
        verifyMessages(dappChain, 2, "my-topic", 0, block2Messages, block0Messages + block2Messages)
    }

    private fun verifyMessages(dappChain: NodeSet,
                               height: Long,
                               topic: String,
                               expectedPreviousMessageBlockHeight: Long,
                               expectedMessages: List<String>,
                               expectedAllMessages: List<String>
    ) {
        for (node in dappChain.nodes()) {
            withReadConnection(node.postchainContext.storage, dappChain.chain) {
                val blockQueries = node.getBlockchainInstance(dappChain.chain).blockchainEngine.getBlockQueries()
                val blockRid = blockQueries.getBlockRid(height).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderData.fromBinary(blockHeader.rawData)
                val expectedHash = TopicHeaderData.calculateMessagesHash(
                        expectedMessages.map { message -> cryptoSystem.digest(GtvEncoder.encodeGtv(gtv(message))) },
                        cryptoSystem
                )
                val topicHeader = decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()[topic]!!.asDict()
                assertContentEquals(
                        expectedHash,
                        topicHeader["hash"]!!.asByteArray()
                )

                assertEquals(expectedPreviousMessageBlockHeight, topicHeader["prev_message_block_height"]!!.asInteger())

                val allMessages =
                        query(node, it, dappChain.chain, "icmf_get_all_messages", gtv(mapOf("topic" to gtv(topic), "height" to gtv(0))))
                assertEquals(expectedAllMessages.size, allMessages.asArray().size)
                expectedAllMessages.forEachIndexed { index, expectedMessage ->
                    assertEquals(expectedMessage, allMessages.asArray()[index].asString())
                }

                val messages =
                        query(node, it, dappChain.chain, "icmf_get_messages", gtv(mapOf("topic" to gtv(topic), "height" to gtv(height))))
                assertEquals(expectedMessages.size, messages.asArray().size)
                expectedMessages.forEachIndexed { index, expectedMessage ->
                    assertEquals(expectedMessage, messages.asArray()[index].asString())
                }
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
