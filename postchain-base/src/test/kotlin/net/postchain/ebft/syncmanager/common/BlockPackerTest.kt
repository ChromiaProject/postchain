package net.postchain.ebft.syncmanager.common

import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.ebft.message.CompleteBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@Suppress("UNUSED_PARAMETER")
class BlockPackerTest {

    private val nodeHex = "121212"
    private val theOnlyOtherNode = NodeRid(nodeHex.hexStringToByteArray())
    private val smallBlockBytes = ByteArray(1000)
    private val bigBlockBytes = ByteArray(10_000_000)
    private val packetVersion = 1L
    private var mockedCb: CompleteBlock = mock {
        on { encoded(any()) } doReturn lazy { smallBlockBytes }
    }

    private fun dummyGetBlockAtHeight(height: Long): BlockDataWithWitness = mock()

    private fun dummyBuildFromBlockDataWithWitness(height: Long, blockData: BlockDataWithWitness): CompleteBlock {
        return mockedCb

    }

    @Test
    fun `BlockPacker returns full BlockRange and myHeight is not reached`() {
        val startAtHeight = 0L
        val myHeight = 50L
        val packedBlocks = mutableListOf<CompleteBlock>()

        val allFit = BlockPacker.packBlockRange(
                theOnlyOtherNode,
                packetVersion,
                startAtHeight,
                myHeight,
                ::dummyGetBlockAtHeight,
                ::dummyBuildFromBlockDataWithWitness,
                packedBlocks)

        assertTrue(allFit)
        assertEquals(BlockPacker.MAX_BLOCKS_IN_PACKAGE, packedBlocks.size)
    }

    @Test
    fun `BlockPacker returns full BlockRange and myHeight is reached`() {
        val startAtHeight = 41L
        val myHeight = 50L
        val packedBlocks = mutableListOf<CompleteBlock>()

        val allFit = BlockPacker.packBlockRange(
                theOnlyOtherNode,
                packetVersion,
                startAtHeight,
                myHeight,
                ::dummyGetBlockAtHeight,
                ::dummyBuildFromBlockDataWithWitness,
                packedBlocks)

        assertTrue(allFit)
        assertEquals(BlockPacker.MAX_BLOCKS_IN_PACKAGE, packedBlocks.size)
    }

    @Test
    fun `BlockPacker returns not full BlockRange because myHeight is reached`() {
        val startAtHeight = 45L
        val myHeight = 50L
        val packedBlocks = mutableListOf<CompleteBlock>()

        val allFit = BlockPacker.packBlockRange(
                theOnlyOtherNode,
                packetVersion,
                startAtHeight,
                myHeight,
                ::dummyGetBlockAtHeight,
                ::dummyBuildFromBlockDataWithWitness,
                packedBlocks)

        assertTrue(allFit)
        assertEquals(6, packedBlocks.size)
    }

    @Test
    fun `BlockPacker returns not full BlockRange because MAX_PACKAGE_CONTENT_BYTES is reached`() {
        val startAtHeight = 0L
        val myHeight = 20L
        val packedBlocks = mutableListOf<CompleteBlock>()
        mockedCb = mock {
            on { encoded(any()) } doReturn lazy { bigBlockBytes }
        }

        val allFit = BlockPacker.packBlockRange(
                theOnlyOtherNode,
                packetVersion,
                startAtHeight,
                myHeight,
                ::dummyGetBlockAtHeight,
                ::dummyBuildFromBlockDataWithWitness,
                packedBlocks)

        assertFalse(allFit)
        val expectedBlocks = BlockPacker.MAX_PACKAGE_CONTENT_BYTES / bigBlockBytes.size
        assertEquals(expectedBlocks, packedBlocks.size)
    }
}