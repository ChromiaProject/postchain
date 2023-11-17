package net.postchain.ebft.syncmanager.readonly

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.base.BaseBlockHeader
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.crypto.Signature
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.network.CommunicationManager
import net.postchain.network.ReceivedPacket
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class ForceReadOnlyMessageProcessorTest {

    private val nodeRid = NodeRid(byteArrayOf())
    private val baseBlockHeader: BaseBlockHeader = mock {
        on { rawData } doReturn "header".toByteArray()
    }
    private val blockWitness: BlockWitness = mock {
        on { getRawData() } doReturn "witness".toByteArray()
    }

    @Test
    fun `GetBlockAtHeight processed where requested height is less or equal to lastBlockHeight`() {
        // setup
        val lastBlockHeight = 10L
        val completionStage: CompletionStage<BlockDataWithWitness?> = CompletableFuture.completedStage(
                BlockDataWithWitness(baseBlockHeader, emptyList(), blockWitness)
        )
        val blockQueries: BlockQueries = mock {
            on { getBlockAtHeight(any(), any()) } doReturn completionStage
        }
        val commManager: CommunicationManager<EbftMessage> = mock {
            on { getPackets() } doReturn mutableListOf(
                    ReceivedPacket(nodeRid, 1L, GetBlockAtHeight(5)),
                    ReceivedPacket(nodeRid, 1L, GetBlockAtHeight(10))
            )
        }
        val sut = ForceReadOnlyMessageProcessor(blockQueries, commManager, lastBlockHeight)

        // action
        sut.processMessages()

        // verification
        argumentCaptor<EbftMessage>().apply {
            verify(commManager, times(2)).sendPacket(capture(), eq(nodeRid))
            assertThat((firstValue as? CompleteBlock)?.height).isEqualTo(5)
            assertThat((secondValue as? CompleteBlock)?.height).isEqualTo(10)
        }
    }

    @Test
    fun `GetBlockAtHeight processed where requested height is greater than lastBlockHeight`() {
        // setup
        val lastBlockHeight = 0L
        val commManager: CommunicationManager<EbftMessage> = mock {
            on { getPackets() } doReturn mutableListOf(
                    ReceivedPacket(nodeRid, 1L, GetBlockAtHeight(5)),
                    ReceivedPacket(nodeRid, 1L, GetBlockAtHeight(10))
            )
        }
        val sut = ForceReadOnlyMessageProcessor(mock(), commManager, lastBlockHeight)

        // action
        sut.processMessages()

        // verification
        verify(commManager, never()).sendPacket(any(), eq(nodeRid))
    }

    @Test
    fun `GetBlockRange processed, sendBlockRangeFromHeight is called regardless of whether requestedHeight is greater or less than lastBlockHeight`() {
        // setup
        val lastBlockHeight = 10L
        val completionStage: CompletionStage<BlockDataWithWitness?> = CompletableFuture.completedStage(
                BlockDataWithWitness(baseBlockHeader, emptyList(), blockWitness)
        )
        val blockQueries: BlockQueries = mock {
            on { getBlockAtHeight(any(), any()) } doReturn completionStage
        }
        val commManager: CommunicationManager<EbftMessage> = mock {
            on { getPackets() } doReturn mutableListOf(
                    ReceivedPacket(nodeRid, 1L, GetBlockRange(5)),
                    ReceivedPacket(nodeRid, 1L, GetBlockRange(10)),
                    ReceivedPacket(nodeRid, 1L, GetBlockRange(15))
            )
        }
        val sut = spy(ForceReadOnlyMessageProcessor(blockQueries, commManager, lastBlockHeight))

        // action
        sut.processMessages()

        // verification
        argumentCaptor<Long>().apply {
            verify(sut, times(3)).sendBlockRangeFromHeight(any(), capture(), eq(10L))
            assertThat(firstValue).isEqualTo(5)
            assertThat(secondValue).isEqualTo(10)
            assertThat(thirdValue).isEqualTo(15)
        }
    }

    @Test
    fun `GetBlockHeaderAndBlock processed, sendBlockHeaderAndBlock is called regardless of whether requestedHeight is greater or less than lastBlockHeight`() {
        // setup
        val lastBlockHeight = 10L
        val completionStage: CompletionStage<BlockDataWithWitness?> = CompletableFuture.completedStage(
                BlockDataWithWitness(baseBlockHeader, emptyList(), blockWitness)
        )
        val blockQueries: BlockQueries = mock {
            on { getBlockAtHeight(any(), any()) } doReturn completionStage
        }
        val commManager: CommunicationManager<EbftMessage> = mock {
            on { getPackets() } doReturn mutableListOf(
                    ReceivedPacket(nodeRid, 1L, GetBlockHeaderAndBlock(5)),
                    ReceivedPacket(nodeRid, 1L, GetBlockHeaderAndBlock(10)),
                    ReceivedPacket(nodeRid, 1L, GetBlockHeaderAndBlock(15))
            )
        }
        val sut = spy(ForceReadOnlyMessageProcessor(blockQueries, commManager, lastBlockHeight))

        // action
        sut.processMessages()

        // verification
        argumentCaptor<Long>().apply {
            verify(sut, times(3)).sendBlockHeaderAndBlock(any(), capture(), eq(10L))
            assertThat(firstValue).isEqualTo(5)
            assertThat(secondValue).isEqualTo(10)
            assertThat(thirdValue).isEqualTo(15)
        }
    }

    @Test
    fun `GetBlockSignature processed where requested height is less or equal to lastBlockHeight`() {
        // setup
        val lastBlockHeight = 10L
        val completionStage5: CompletionStage<BlockDetail?> = CompletableFuture.completedStage(
                mock { on { height } doReturn 5 }
        )
        val completionStage10: CompletionStage<BlockDetail?> = CompletableFuture.completedStage(
                mock { on { height } doReturn 10 }
        )
        val completionStageSignature: CompletionStage<Signature> = CompletableFuture.completedStage(
                Signature(byteArrayOf(1), "signature".toByteArray())
        )
        val blockQueries: BlockQueries = mock()
        whenever(blockQueries.getBlock(any(), any())).doReturn(completionStage5).doReturn(completionStage10)
        whenever(blockQueries.getBlockSignature(any())).doReturn(completionStageSignature)

        val commManager: CommunicationManager<EbftMessage> = mock {
            on { getPackets() } doReturn mutableListOf(
                    ReceivedPacket(nodeRid, 1L, GetBlockSignature(byteArrayOf(1))),
                    ReceivedPacket(nodeRid, 1L, GetBlockSignature(byteArrayOf(2)))
            )
        }
        val sut = spy(ForceReadOnlyMessageProcessor(blockQueries, commManager, lastBlockHeight))

        // action
        sut.processMessages()

        // verification
        argumentCaptor<ByteArray>().apply {
            verify(sut, times(2)).sendBlockSignature(eq(nodeRid), capture())
            assertThat(firstValue).isContentEqualTo(byteArrayOf(1))
            assertThat(secondValue).isContentEqualTo(byteArrayOf(2))
        }
    }

    @Test
    fun `GetBlockSignature processed where requested height greater than lastBlockHeight or block not found`() {
        // setup
        val lastBlockHeight = 10L
        val completionStageBlockNotFound: CompletionStage<BlockDetail?> = CompletableFuture.completedStage(null)
        val completionStageGreaterHeight: CompletionStage<BlockDetail?> = CompletableFuture.completedStage(
                mock { on { height } doReturn 15L }
        )
        val blockQueries: BlockQueries = mock()
        whenever(blockQueries.getBlock(any(), any())).doReturn(completionStageBlockNotFound).doReturn(completionStageGreaterHeight)

        val commManager: CommunicationManager<EbftMessage> = mock {
            on { getPackets() } doReturn mutableListOf(
                    ReceivedPacket(nodeRid, 1L, GetBlockSignature(byteArrayOf(1))),
                    ReceivedPacket(nodeRid, 1L, GetBlockSignature(byteArrayOf(2)))
            )
        }
        val sut = spy(ForceReadOnlyMessageProcessor(blockQueries, commManager, lastBlockHeight))

        // action
        sut.processMessages()

        // verification
        verify(sut, never()).sendBlockSignature(any(), any())
    }
}