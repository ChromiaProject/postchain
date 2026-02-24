package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.postchain.base.BaseBlockHeader
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockRid
import net.postchain.common.BlockchainRid
import net.postchain.core.ValidationResult
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
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

    @Test
    fun testExtraDataValueMismatchMessage() {
        val extraData1 = mapOf("key1" to gtv("value1"), "key2" to gtv("value2"))
        val extraData2 = mapOf("key1" to gtv("value1"), "key2" to gtv("mismatch"))

        val bcRid = BlockchainRid.buildRepeat(0)
        val initialBlockData = InitialBlockData(bcRid, 1L, 1L, ByteArray(32), 1L, 1000L, null)
        val rootHash = ByteArray(32)
        val timestamp = 1000L

        val calculator = GtvMerkleHashCalculatorV2(::sha256Digest)
        val header = BaseBlockHeader.make(calculator, initialBlockData, rootHash, timestamp, extraData1)

        val result = GenericBlockHeaderValidator.advancedValidateAgainstKnownBlocks(
                blockHeader = header,
                initialBlockData = initialBlockData,
                expectedMerkleRootHash = { rootHash },
                blockRidFromHeight = { _ -> null },
                currentBlockTimestamp = 500L,
                currentTimestamp = 1000L,
                maxBlockFutureTime = 1000L,
                nrOfDependencies = 0,
                extraData = extraData2,
                subjects = arrayOf(),
                checkPrimaryField = false,
                skipValidationFields = setOf(),
                skipRootHashValidation = true
        )

        assertThat(result.result).isEqualTo(ValidationResult.Result.INVALID_EXTRA_DATA)
        println("Actual error message: ${result.message}")

        // Assert improved message
        assertThat(result.message).contains("key2: \"value2\" != \"mismatch\"")
    }

    @Test
    fun testExtraDataKeyMismatchMessage() {
        val extraData1 = mapOf("key1" to gtv("value1"), "headerOnly" to gtv("extra"))
        val extraData2 = mapOf("key1" to gtv("value1"), "expectedOnly" to gtv("missing"))

        val bcRid = BlockchainRid.buildRepeat(0)
        val initialBlockData = InitialBlockData(bcRid, 1L, 1L, ByteArray(32), 1L, 1000L, null)
        val rootHash = ByteArray(32)
        val timestamp = 1000L

        val calculator = GtvMerkleHashCalculatorV2(::sha256Digest)
        val header = BaseBlockHeader.make(calculator, initialBlockData, rootHash, timestamp, extraData1)

        val result = GenericBlockHeaderValidator.advancedValidateAgainstKnownBlocks(
                blockHeader = header,
                initialBlockData = initialBlockData,
                expectedMerkleRootHash = { rootHash },
                blockRidFromHeight = { _ -> null },
                currentBlockTimestamp = 500L,
                currentTimestamp = 1000L,
                maxBlockFutureTime = 1000L,
                nrOfDependencies = 0,
                extraData = extraData2,
                subjects = arrayOf(),
                checkPrimaryField = false,
                skipValidationFields = setOf(),
                skipRootHashValidation = true
        )

        assertThat(result.result).isEqualTo(ValidationResult.Result.INVALID_EXTRA_DATA)
        println("Actual error message: ${result.message}")

        assertThat(result.message).contains("expectedOnly: null != \"missing\"")
        assertThat(result.message).contains("headerOnly: \"extra\" != null")
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