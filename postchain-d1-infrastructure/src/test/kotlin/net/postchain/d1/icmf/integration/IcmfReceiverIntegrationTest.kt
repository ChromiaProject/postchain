package net.postchain.d1.icmf.integration

import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.icmf.GlobalTopicPipe
import net.postchain.d1.icmf.ICMF_BLOCK_HEADER_EXTRA
import net.postchain.d1.icmf.TopicHeaderData
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

private const val CHAIN_ID = 1

class IcmfReceiverIntegrationTest : GtxTxIntegrationTestSetup() {

    @BeforeEach
    fun setup() {
        PostchainClientMocks.clearMocks()
    }

    @Test
    fun happyReceiver() {
        val queryResponse = GlobalTopicPipe.SignedBlockHeaderWithAnchorHeight(
                GtvEncoder.encodeGtv(BlockHeaderData(
                        gtv(BlockchainRid.buildRepeat(1).data),
                        gtv(BlockchainRid.buildRepeat(1).data),
                        gtv(ByteArray(32)),
                        gtv(0),
                        gtv(0),
                        GtvNull,
                        gtv(mapOf(
                                ICMF_BLOCK_HEADER_EXTRA to gtv(
                                        "my-topic" to TopicHeaderData(ByteArray(32), -1L).toGtv()
                                )
                        ))
                ).toGtv()),
                ByteArray(32),
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
        PostchainClientMocks.addMockClient(BlockchainRid.buildRepeat(0), anchorClientMock)

        val senderClientMock: PostchainClient = mock {}
        PostchainClientMocks.addMockClient(BlockchainRid.buildRepeat(1), senderClientMock)

        val mapBcFiles: Map<Int, String> = mapOf(
                CHAIN_ID to "/net/postchain/icmf/integration/receiver/blockchain_config_1.xml",
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        runXNodes(sysSetup)

        buildBlock(CHAIN_ID.toLong(), 0)
    }
}