package net.postchain.ebft.messages

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.micrometer.core.instrument.Timer
import net.postchain.base.BaseBlockHeader
import net.postchain.base.createBlockHeader
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetUnfinishedBlock
import net.postchain.ebft.message.MessageDurationTracker
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.metrics.MessageDurationTrackerMetricsFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.TimeUnit

class MessageDurationTrackerTest {

    private val nodeRid1 = NodeRid("node1".toByteArray())
    private val nodeRid2 = NodeRid("node2".toByteArray())
    private val nodeRid3 = NodeRid("node3".toByteArray())
    private val appConfig: AppConfig = mock {
        on { trackedEbftMessageMaxKeepTimeMs } doReturn 60
    }
    private val timer: Timer = mock()
    private val metricsFactory: MessageDurationTrackerMetricsFactory = mock {
        on { createTimer(any(), any()) } doReturn timer
    }

    private var currentMs = 10L

    private lateinit var sut: MessageDurationTracker

    companion object {
        private val blockchainRID = BlockchainRid.ZERO_RID
        private val prevBlockRID0 = ByteArray(32) { if (it == 31) 99 else 0 }
        private val cryptoSystem = Secp256K1CryptoSystem()
        private val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        private val header0 = createBlockHeader(blockchainRID, 2L, 0, prevBlockRID0, 0, merkleHashCalculator)
        private val baseBlockHeader = BaseBlockHeader(header0.rawData, merkleHashCalculator)

        @JvmStatic
        private fun senderReceiverMessages() = listOf(
                arrayOf(GetBlockHeaderAndBlock(0L), BlockHeader(header0.rawData, "".toByteArray(), 0L)),
                arrayOf(GetBlockRange(42L), BlockRange(42L, false, emptyList())),
                arrayOf(GetBlockSignature(baseBlockHeader.blockRID), BlockSignature(baseBlockHeader.blockRID, mock())),
                arrayOf(GetBlockAtHeight(0L), CompleteBlock(mock(), 0L, "".toByteArray())),
        )
    }

    @BeforeEach
    fun beforeEach() {
        sut = MessageDurationTracker(appConfig, metricsFactory, { msg -> msg.javaClass.simpleName }, nanoProvider())
    }

    @Test
    fun `No sent message should not give a time`() {
        // execute & verify
        assertThat(sut.receive(nodeRid1, BlockRange(42L, false, emptyList()))).isNull()
        // verify
        verify(timer, never()).record(0, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Duration between sent and received should be correct`() {
        // setup
        sut.send(nodeRid1, GetBlockRange(42L))
        addTime(10)
        // execute & verify
        assertThat(sut.receive(nodeRid1, BlockRange(42L, false, emptyList()))!!.inWholeMilliseconds).isEqualTo(10)
        // verify
        verify(timer).record(10, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Sent does not match received should not register`() {
        // setup
        sut.send(nodeRid1, GetBlockHeaderAndBlock(42L))
        addTime(10)
        // execute & verify
        assertThat(sut.receive(nodeRid1, BlockRange(42L, false, emptyList()))).isNull()
        // verify
        verify(timer, never()).record(any(), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `Multiple sent messages should use first when receiving response`() {
        // setup
        val sentMessage = GetBlockRange(42L)
        sut.send(nodeRid1, sentMessage)
        addTime(10)
        sut.send(nodeRid1, sentMessage)
        addTime(10)
        // execute & verify
        assertThat(sut.receive(nodeRid1, BlockRange(42L, false, emptyList()))!!.inWholeMilliseconds).isEqualTo(20)
        // verify
        verify(timer).record(20, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Multiple received same messages should only register first`() {
        // setup
        val sentMessage = GetBlockRange(42L)
        val receivedMessage = BlockRange(42L, false, emptyList())
        sut.send(nodeRid1, sentMessage)
        addTime(10)
        sut.send(nodeRid1, sentMessage)
        addTime(10)
        // execute & verify
        assertThat(sut.receive(nodeRid1, receivedMessage)!!.inWholeMilliseconds).isEqualTo(20)
        assertThat(sut.receive(nodeRid1, receivedMessage)).isNull()
        // verify
        verify(timer).record(20, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Multiple messages to different targets`() {
        // setup
        val sentMessage = GetBlockRange(42L)
        val receivedMessage = BlockRange(42L, false, emptyList())
        sut.send(listOf(nodeRid1, nodeRid2, nodeRid3), sentMessage)
        addTime(10)
        // execute & verify
        assertThat(sut.receive(nodeRid1, receivedMessage)!!.inWholeMilliseconds).isEqualTo(10)
        addTime(10)
        assertThat(sut.receive(nodeRid2, receivedMessage)!!.inWholeMilliseconds).isEqualTo(20)
        addTime(10)
        assertThat(sut.receive(nodeRid3, receivedMessage)!!.inWholeMilliseconds).isEqualTo(30)
        // verify
        verify(timer, times(3)).record(any(), eq(TimeUnit.MILLISECONDS))
    }

    @ParameterizedTest
    @MethodSource("senderReceiverMessages")
    fun `Verify sender and receiver messages`(sender: EbftMessage, receiver: EbftMessage) {
        // setup
        sut.send(nodeRid1, sender)
        addTime(10)
        // execute & verify
        assertThat(sut.receive(nodeRid1, receiver)!!.inWholeMilliseconds).isEqualTo(10)
        // verify
        verify(timer).record(10, TimeUnit.MILLISECONDS)
    }

    @Nested
    inner class UnfinishedBlock {
        @Test
        fun `should work with GetUnfinishedBlock`() {
            // setup
            sut.send(nodeRid1, GetUnfinishedBlock(baseBlockHeader.blockRID))
            addTime(10)
            // execute & verify
            assertThat(sut.receive(nodeRid1, UnfinishedBlock(header0.rawData, emptyList()), baseBlockHeader)!!.inWholeMilliseconds).isEqualTo(10)
            // verify
            verify(timer).record(10, TimeUnit.MILLISECONDS)
        }

        @Test
        fun `should work with GetBlockHeaderAndBlock`() {
            // setup
            sut.send(nodeRid1, GetBlockHeaderAndBlock(baseBlockHeader.blockHeaderRec.getHeight()))
            addTime(10)
            // execute & verify
            assertThat(sut.receive(nodeRid1, UnfinishedBlock(header0.rawData, emptyList()), baseBlockHeader)!!.inWholeMilliseconds).isEqualTo(10)
            // verify
            verify(timer).record(10, TimeUnit.MILLISECONDS)
        }

        @Test
        fun `should work with both GetUnfinishedBlock and GetBlockHeaderAndBlock`() {
            // setup
            sut.send(nodeRid1, GetUnfinishedBlock(baseBlockHeader.blockRID))
            addTime(10)
            sut.send(nodeRid1, GetBlockHeaderAndBlock(baseBlockHeader.blockHeaderRec.getHeight()))
            addTime(10)
            // execute & verify
            assertThat(sut.receive(nodeRid1, UnfinishedBlock(header0.rawData, emptyList()), baseBlockHeader)!!.inWholeMilliseconds).isEqualTo(20)
            // verify
            verify(timer).record(20, TimeUnit.MILLISECONDS)
        }
    }

    private fun addTime(ms: Long) {
        currentMs += ms
    }

    private fun nanoProvider(): () -> Long {
        return { TimeUnit.MILLISECONDS.toNanos(currentMs) }
    }
}