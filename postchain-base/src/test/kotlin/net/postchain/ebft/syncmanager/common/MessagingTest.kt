package net.postchain.ebft.syncmanager.common

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockHeader
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockRid
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.crypto.Signature
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.network.CommunicationManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class MessagingTest {

    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockRID = BlockRid.buildFromHex(brid).data
    private val node = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    private val nodeRid = NodeRid.fromHex(node)
    private val height = 10L
    private val lastBlockHeight = 10L
    private val header: ByteArray = "header".toByteArray()
    private val witness: ByteArray = "witness".toByteArray()
    private val transactions: List<ByteArray> = listOf("tx1".toByteArray())

    private val commManager: CommunicationManager<EbftMessage> = mock()
    private val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(lastBlockHeight)
        on { getLastBlockHeight() } doReturn completionStage
    }
    private val baseBlockHeader: BaseBlockHeader = mock {
        on { rawData } doReturn header
    }
    private val blockWitness: BlockWitness = mock {
        on { getRawData() } doReturn witness
    }
    private val blockPacker: BlockPacker = mock()

    private lateinit var sut: Messaging

    @BeforeEach
    fun setup() {
        sut = object : Messaging(blockQueries, commManager, blockPacker) {}
    }

    ///// message: sendBlockAtHeight /////
    @Test
    fun `sendBlockAtHeight should retrieve and send block at height`() {
        // setup
        val blockDataWithWitness = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        val completionStage: CompletionStage<BlockDataWithWitness> = CompletableFuture.completedStage(blockDataWithWitness)
        doReturn(completionStage).whenever(blockQueries).getBlockAtHeight(anyLong(), anyBoolean())
        doNothing().whenever(commManager).sendPacket(isA(), eq(nodeRid))
        // execute
        sut.sendBlockAtHeight(nodeRid, height)
        // verify
        verify(commManager).sendPacket(isA(), eq(nodeRid))
    }

    @Test
    fun `sendBlockAtHeight with missing block at height should do nothing`() {
        // setup
        val completionStage: CompletionStage<BlockDataWithWitness> = CompletableFuture.completedStage(null)
        doReturn(completionStage).whenever(blockQueries).getBlockAtHeight(anyLong(), anyBoolean())
        doNothing().whenever(commManager).sendPacket(isA(), eq(nodeRid))
        // execute
        sut.sendBlockAtHeight(nodeRid, height)
        // verify
        verify(commManager, never()).sendPacket(isA(), eq(nodeRid))
    }

    ///// sendBlockRangeFromHeight /////
    @Test
    fun `sendBlockRangeFromHeight should pack the blocks and send them to peer`() {
        // setup
        val myHeight = 5L
        val startAtHeight = 1L
        doReturn(true).whenever(blockPacker).packBlockRange(isA(), anyLong(), anyLong(), any(), any(), anyList())
        // execute
        sut.sendBlockRangeFromHeight(nodeRid, startAtHeight, myHeight)
        // verify
        verify(blockPacker).packBlockRange(eq(nodeRid), eq(startAtHeight), eq(myHeight), any(), any(), anyList())
        argumentCaptor<BlockRange>().apply {
            verify(commManager).sendPacket(capture(), eq(nodeRid))
            assertThat(firstValue.startAtHeight).isEqualTo(startAtHeight)
            assertThat(firstValue.isFull).isFalse()
        }
    }

    @Test
    fun `sendBlockRangeFromHeight with too many blocks should pack some blocks and send them to peer`() {
        // setup
        val myHeight = 5L
        val startAtHeight = 1L
        doReturn(false).whenever(blockPacker).packBlockRange(isA(), anyLong(), anyLong(), any(), any(), anyList())
        // execute
        sut.sendBlockRangeFromHeight(nodeRid, startAtHeight, myHeight)
        // verify
        verify(blockPacker).packBlockRange(eq(nodeRid), eq(startAtHeight), eq(myHeight), any(), any(), anyList())
        argumentCaptor<BlockRange>().apply {
            verify(commManager).sendPacket(capture(), eq(nodeRid))
            assertThat(firstValue.startAtHeight).isEqualTo(startAtHeight)
            assertThat(firstValue.isFull).isTrue()
        }
    }

    ///// sendBlockHeaderAndBlock /////
    @Test
    fun `sendBlockHeaderAndBlock with my height is -1 should send a packet with an empty header`() {
        // setup
        val myHeight = -1L
        val requestedHeight = 1L
        // execute
        sut.sendBlockHeaderAndBlock(nodeRid, requestedHeight, myHeight)
        // verify
        argumentCaptor<BlockHeader>().apply {
            verify(commManager).sendPacket(capture(), eq(nodeRid))
            assertThat(firstValue.header).isEmpty()
            assertThat(firstValue.witness).isEmpty()
            assertThat(firstValue.requestedHeight).isEqualTo(requestedHeight)
        }
    }

    @Test
    fun `sendBlockHeaderAndBlock with missing block at height should throw exception`() {
        // setup
        val myHeight = 5L
        val requestedHeight = 10L
        val completionStage: CompletionStage<BlockDataWithWitness> = CompletableFuture.completedStage(null)
        doReturn(completionStage).whenever(blockQueries).getBlockAtHeight(anyLong(), anyBoolean())
        // execute
        val exception = assertThrows<ProgrammerMistake> {
            sut.sendBlockHeaderAndBlock(nodeRid, requestedHeight, myHeight)
        }
        // verify
        assertThat(exception.message).isEqualTo("Block at height: $myHeight doesn't exist.")
    }

    @Test
    fun `sendBlockHeaderAndBlock with my height less than requested height should send block from requested height`() {
        // setup
        val myHeight = 5L
        val requestedHeight = 10L
        val blockDataWithWitness = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        val completionStage: CompletionStage<BlockDataWithWitness> = CompletableFuture.completedStage(blockDataWithWitness)
        doReturn(completionStage).whenever(blockQueries).getBlockAtHeight(anyLong(), anyBoolean())
        // execute
        sut.sendBlockHeaderAndBlock(nodeRid, requestedHeight, myHeight)
        // verify
        argumentCaptor<BlockHeader>().apply {
            verify(commManager).sendPacket(capture(), eq(nodeRid))
            assertThat(firstValue.header).isEqualTo(header)
            assertThat(firstValue.witness).isEqualTo(witness)
            assertThat(firstValue.requestedHeight).isEqualTo(requestedHeight)
        }
    }

    @Test
    fun `sendBlockHeaderAndBlock with my height gte requested should send header and unfinished block`() {
        // setup
        val myHeight = 10L
        val requestedHeight = 10L
        val blockDataWithWitness = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        val completionStage: CompletionStage<BlockDataWithWitness> = CompletableFuture.completedStage(blockDataWithWitness)
        doReturn(completionStage).whenever(blockQueries).getBlockAtHeight(anyLong(), anyBoolean())
        // execute
        sut.sendBlockHeaderAndBlock(nodeRid, requestedHeight, myHeight)
        // verify
        argumentCaptor<EbftMessage>().apply {
            verify(commManager, times(2)).sendPacket(capture(), eq(nodeRid))
            val bh = firstValue as BlockHeader
            assertThat(bh.header).isEqualTo(header)
            assertThat(bh.witness).isEqualTo(witness)
            assertThat(bh.requestedHeight).isEqualTo(requestedHeight)
            val ub = secondValue as UnfinishedBlock
            assertThat(ub.header).isEqualTo(header)
            assertThat(ub.transactions).isEqualTo(transactions)
        }
    }

    ///// sendBlockSignature /////
    @Test
    fun `sendBlockSignature should send block signature`() {
        // setup
        val subjectID: ByteArray = "subjectID".toByteArray()
        val data: ByteArray = "data".toByteArray()
        val signature = Signature(subjectID, data)
        val completionStage: CompletionStage<Signature> = CompletableFuture.completedStage(signature)
        doReturn(completionStage).whenever(blockQueries).getBlockSignature(isA())
        // execute
        sut.sendBlockSignature(nodeRid, blockRID)
        // verify
        argumentCaptor<BlockSignature>().apply {
            verify(commManager).sendPacket(capture(), eq(nodeRid))
            assertThat(firstValue.blockRID).isEqualTo(blockRID)
            assertThat(firstValue.sig.subjectID).isEqualTo(subjectID)
            assertThat(firstValue.sig.data).isEqualTo(data)
        }
    }

    @Test
    fun `sendBlockSignature with error should do nothing`() {
        // setup
        val completionStage: CompletionStage<Signature> = CompletableFuture.failedStage(Exception("Failed"))
        doReturn(completionStage).whenever(blockQueries).getBlockSignature(isA())
        // execute
        sut.sendBlockSignature(nodeRid, blockRID)
        // verify
        verify(commManager, never()).sendPacket(isA(), eq(nodeRid))
    }
}