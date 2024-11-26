package net.postchain.base.data

import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockRid
import net.postchain.common.BlockchainRid
import net.postchain.core.ValidationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Just trying to provoke some of the simpler validation errors in here
 */
class GenericBlockHeaderValidatorTest {

    val myBcRid  = BlockchainRid.buildRepeat(7)

    val myPrevBlockRid   = "1234123412341234123412341234123412341234123412341234123412341234".hexStringToByteArray()
    val myBlockRid1      = "1111111111111111111111111111111111111111111111111111111111111111".hexStringToByteArray()
    val myBlockRid2      = "2222222222222222222222222222222222222222222222222222222222222222".hexStringToByteArray()
    val myBlockRid3      = "3333333333333333333333333333333333333333333333333333333333333333".hexStringToByteArray()
    val myBlockRid4      = "4444444444444444444444444444444444444444444444444444444444444444".hexStringToByteArray()

    @Test
    fun testHappy() {
        val prevHeader = MinimalBlockHeaderInfo(BlockRid(myPrevBlockRid), null, 3)

        val headerMap = mutableMapOf(
            4L to MinimalBlockHeaderInfo(BlockRid(myBlockRid1), BlockRid(myPrevBlockRid), 4),
            5L to MinimalBlockHeaderInfo(BlockRid(myBlockRid2), BlockRid(myBlockRid1), 5),
            6L to MinimalBlockHeaderInfo(BlockRid(myBlockRid3), BlockRid(myBlockRid2), 6),
            7L to MinimalBlockHeaderInfo(BlockRid(myBlockRid4), BlockRid(myBlockRid3), 7)
        )

        val res = GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(myBcRid, headerMap, prevHeader, ::getBlockRid)

        assertEquals(res.result, ValidationResult.Result.OK, "Should be ok")
    }

    @Test
    fun testBadPrevBlock() {
        val prevHeader = MinimalBlockHeaderInfo(BlockRid(myPrevBlockRid), null, 3)

        val headerMap = mutableMapOf(
            4L to MinimalBlockHeaderInfo(BlockRid(myBlockRid1), BlockRid(myPrevBlockRid), 4),
            5L to MinimalBlockHeaderInfo(BlockRid(myBlockRid2), BlockRid(myBlockRid1), 5),
            6L to MinimalBlockHeaderInfo(BlockRid(myBlockRid3), BlockRid(myBlockRid4), 6), // Bad prev block
            7L to MinimalBlockHeaderInfo(BlockRid(myBlockRid4), BlockRid(myBlockRid3), 7)
        )

        val res = GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(myBcRid, headerMap, prevHeader, ::getBlockRid)

        assertEquals(res.result, ValidationResult.Result.PREV_BLOCK_MISMATCH, "Must detect bad prev block at height 6")
    }

    @Test
    fun testBlockMissing() {
        val prevHeader = MinimalBlockHeaderInfo(BlockRid(myPrevBlockRid), null, 3)

        val headerMap = mutableMapOf(
            4L to MinimalBlockHeaderInfo(BlockRid(myBlockRid1), BlockRid(myPrevBlockRid), 4),
            6L to MinimalBlockHeaderInfo(BlockRid(myBlockRid3), BlockRid(myBlockRid1), 6), // No height 5
            7L to MinimalBlockHeaderInfo(BlockRid(myBlockRid4), BlockRid(myBlockRid3), 7)
        )

        val res = GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(myBcRid, headerMap, prevHeader, ::getBlockRid)

        assertEquals(res.result, ValidationResult.Result.BLOCK_FROM_THE_FUTURE, "Must detect missing block 5")
    }

    @Test
    fun testOldBlockNotFound() {
        val prevHeader = MinimalBlockHeaderInfo(BlockRid(myPrevBlockRid), null, 3)

        val headerMap = mutableMapOf(
            4L to MinimalBlockHeaderInfo(BlockRid(myBlockRid1), BlockRid(myPrevBlockRid), 4),
            5L to MinimalBlockHeaderInfo(BlockRid(myBlockRid2), BlockRid(myBlockRid1), 5),
            1L to MinimalBlockHeaderInfo(BlockRid(myBlockRid3), BlockRid(myBlockRid2), 1), // We have configured the mocked DB to not know about this height
            7L to MinimalBlockHeaderInfo(BlockRid(myBlockRid4), BlockRid(myBlockRid3), 7)
        )

        val res = GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(myBcRid, headerMap, prevHeader, ::getBlockRid)

        assertEquals(res.result, ValidationResult.Result.OLD_BLOCK_NOT_FOUND, "Must detect missing block") // Agreed, this shouldn't happen IRL
    }


    @Test
    fun testSplitInTheChain() {
        val prevHeader = MinimalBlockHeaderInfo(BlockRid(myPrevBlockRid), null, 3)

        val headerMap = mutableMapOf(
            3L to MinimalBlockHeaderInfo(BlockRid(myBlockRid1), BlockRid(myPrevBlockRid), 3)
        )

        val res = GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(myBcRid, headerMap, prevHeader, ::getBlockRid)

        assertEquals(res.result, ValidationResult.Result.SPLIT, "Must detect split")
    }

    /**
     * Make sure the fake BC returns the correct height
     */
    fun getBlockRid(height: Long): ByteArray? {
        return when (height) {
            3L -> myPrevBlockRid
            4L -> myBlockRid1
            5L -> myBlockRid2
            6L -> myBlockRid3
            7L -> myBlockRid4
            else -> null
        }
    }
}