package net.postchain.ebft.syncmanager.common

import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.ebft.message.CompleteBlock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

@Suppress("UNUSED_PARAMETER")
class BlockPackerTest {

    val nodeHex = "121212"
    val theOnlyOtherNode = NodeRid(nodeHex.hexStringToByteArray())
    val startAtHeight = 17L
    val myHeight = 19L

    val smallBlockBytes = ByteArray(1000)
    val bigBlockBytes = ByteArray(10_000_000)

    private lateinit var mockedBd: BlockDataWithWitness
    private lateinit var mockedCb: CompleteBlock

    private fun dummyGetBlockAtHeight(height: Long): BlockDataWithWitness? {
        return mockedBd
    }

    private fun dummyBuildFromBlockDataWithWitness(height: Long, blockData: BlockDataWithWitness): CompleteBlock {
        return mockedCb

    }

    /**
     * Here we see that if blocks are small we will happily fill the block list with 10 blocks.
     */
    @Test
    fun happy() {
        val packedBlocks = mutableListOf<CompleteBlock>()
        mockedBd = mock { }
        mockedCb = mock {
            on { encoded } doReturn smallBlockBytes
        }

        val allFit = BlockPacker.packBlockRange(
            theOnlyOtherNode,
            startAtHeight,
            myHeight,
            ::dummyGetBlockAtHeight,
            ::dummyBuildFromBlockDataWithWitness,
            packedBlocks)

        assertTrue(allFit)
        assertEquals(BlockPacker.MAX_BLOCKS_IN_PACKAGE, packedBlocks.size)
    }

    /**
     * For bigger blocks we will only fit a few blocks in the package.
     */
    @Test
    fun only_two_big_blocks_will_fit() {

        val packedBlocks = mutableListOf<CompleteBlock>()
        mockedBd = mock { }
        mockedCb = mock {
            on { encoded } doReturn bigBlockBytes
        }

        val allFit = BlockPacker.packBlockRange(
            theOnlyOtherNode,
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