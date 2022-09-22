package net.postchain.d1.icmf.integration

import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.icmf.GlobalTopicPipe
import net.postchain.d1.icmf.ICMF_BLOCK_HEADER_EXTRA
import net.postchain.d1.icmf.TopicHeaderData
import net.postchain.d1.icmf.integration.IcmfReceiverTestGTXModule.Companion.testMessageTable
import net.postchain.d1.icmf.privKey
import net.postchain.d1.icmf.pubKey
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val CHAIN_ID = 1

class IcmfReceiverIntegrationTest : GtxTxIntegrationTestSetup() {

    @BeforeEach
    fun setup() {
        PostchainClientMocks.clearMocks()
    }

    @Test
    fun happyReceiver() {
        val anchorChainRid = BlockchainRid.buildRepeat(0)
        val senderChainRid = BlockchainRid.buildRepeat(1)

        val messageBody = gtv("hej")
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)

        val blockHeader = BlockHeaderData(
                gtv(senderChainRid.data),
                gtv(senderChainRid.data),
                gtv(ByteArray(32)),
                gtv(0),
                gtv(0),
                GtvNull,
                gtv(mapOf(
                        ICMF_BLOCK_HEADER_EXTRA to gtv(
                                "my-topic" to TopicHeaderData(
                                        cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody)),
                                        -1L).toGtv()
                        )
                ))
        ).toGtv()
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(arrayOf(cryptoSystem.buildSigMaker(pubKey.key, privKey.key).signDigest(blockRid))).getRawData()
        val queryResponse = GlobalTopicPipe.SignedBlockHeaderWithAnchorHeight(
                GtvEncoder.encodeGtv(blockHeader),
                rawWitness,
                0
        ).toGtv()

        val anchorClientMock: PostchainClient = mock {
            on { currentBlockHeightSync() } doReturn 1L
            on {
                querySync("icmf_get_headers_with_messages_between_heights", gtv(mapOf(
                        "topic" to gtv("my-topic"),
                        "from_anchor_height" to gtv(0),
                        "to_anchor_height" to gtv(1)
                )))
            } doReturn gtv(listOf(queryResponse))
        }
        PostchainClientMocks.addMockClient(anchorChainRid, anchorClientMock)

        val senderClientMock: PostchainClient = mock {
            on {
                querySync("icmf_get_messages", gtv(mapOf(
                        "topic" to gtv("my-topic"),
                        "height" to gtv(0)
                )))
            } doReturn gtv(listOf(messageBody))
        }
        PostchainClientMocks.addMockClient(senderChainRid, senderClientMock)

        val mapBcFiles: Map<Int, String> = mapOf(
                CHAIN_ID to "/net/postchain/icmf/integration/receiver/blockchain_config_1.xml",
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        runXNodes(sysSetup)

        buildBlock(CHAIN_ID.toLong(), 0)
        for (node in nodes) {
            withReadConnection(node.postchainContext.storage, CHAIN_ID.toLong()) {
                DatabaseAccess.of(it).apply {
                    val messages = QueryRunner().query(
                            it.conn,
                            "SELECT * FROM ${tableName(it, testMessageTable)}",
                            MapListHandler()
                    )

                    assertEquals(1, messages.size)
                    val message = messages[0]
                    assertContentEquals(senderChainRid.data, message["sender"] as ByteArray)
                    assertEquals("my-topic", message["topic"] as String)
                    assertContentEquals(encodedMessageBody, message["body"] as ByteArray)
                }
            }
        }
    }
}