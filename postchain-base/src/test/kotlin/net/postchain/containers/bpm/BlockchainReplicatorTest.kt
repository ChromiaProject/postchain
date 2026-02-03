package net.postchain.containers.bpm

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.managed.DirectoryDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never

class BlockchainReplicatorTest {

    private lateinit var blockchainReplicator: BlockchainReplicator
    private lateinit var srcContainer: PostchainContainer
    private lateinit var dstContainer: PostchainContainer

    @BeforeEach
    fun setup() {
        blockchainReplicator = BlockchainReplicator(
                BlockchainRid.ZERO_RID.wData,
                mock { on { brid } doReturn BlockchainRid.ZERO_RID },
                mock { on { brid } doReturn BlockchainRid.ZERO_RID },
                0L,
                mock(DirectoryDataSource::class.java)
        ) { null }

        srcContainer = mock<PostchainContainer>()

        var importHeight = 0L
        dstContainer = mock<PostchainContainer> {
            on { importBlocks(any(), anyList()) } doAnswer {
                importHeight += it.getArgument<List<Gtv>>(1).size
                importHeight
            }
        }
    }

    @AfterEach
    fun tearDown() {
        blockchainReplicator.shutdown()
    }

    @Test
    fun testReplicateBlocksWith1SizedBlocks() {

        Mockito.`when`(srcContainer.exportBlocks(any(), any(), any(), any())).thenReturn(buildGtvIntegerSequence(1))

        blockchainReplicator.replicateBlocks(
                10L,
                0L,
                srcContainer,
                dstContainer
        )

        verify(dstContainer, times(10)).importBlocks(
                any(),
                argThat { args -> blocksMatchSequence(args, 1) })
    }

    @Test
    fun testReplicateBlocksWith2SizedBlocks() {

        Mockito.`when`(srcContainer.exportBlocks(any(), any(), any(), any())).thenReturn(buildGtvIntegerSequence(2))

        blockchainReplicator.replicateBlocks(
                10L,
                0L,
                srcContainer,
                dstContainer
        )

        verify(dstContainer, times(5)).importBlocks(
                any(),
                argThat { arg -> blocksMatchSequence(arg, 2) })
    }

    @Test
    fun testReplicationCanceling() {

        Mockito.`when`(srcContainer.exportBlocks(any(), any(), any(), any())).thenReturn(buildGtvIntegerSequence(2))

        // Cancel replication
        blockchainReplicator.cancel()

        // Continue blocks replication
        blockchainReplicator.replicateBlocks(
                10L,
                0L,
                srcContainer,
                dstContainer
        )

        verify(dstContainer, never()).importBlocks(any(), any())
    }

    private fun buildGtvIntegerSequence(upTo: Int): List<GtvInteger> {
        return List(upTo) { GtvInteger(it.toLong()) }
    }

    private fun blocksMatchSequence(blocks: List<Gtv>, expectedSize: Int): Boolean {

        for ((index, gtv) in blocks.withIndex()) {
            if (gtv !is GtvInteger || ((gtv).asInteger().toInt() != index)) {
                return false
            }
        }

        return blocks.size == expectedSize
    }
}