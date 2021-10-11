package net.postchain.base.data

import net.postchain.base.BaseBlockHeader
import net.postchain.core.BlockHeader
import net.postchain.core.BlockRid
import net.postchain.core.InitialBlockData
import net.postchain.core.ValidationResult
import java.util.*

/**
 * Here we put validation that doesn't need complex dependencies like signers etc.
 */
object GenericBlockHeaderValidator {

    /**
     * Checks a bare minimum of things that must be valid for a block header:
     *
     * - check that previous block RID is used in this block
     * - check for correct height
     *   - If height is too low check if it's a split or duplicate
     *   - If too high, it's from the future
     *
     * (Sometimes, like during Anchoring, this is all we can do).
     *
     * @param headerBlockRid is the block hash from the header
     * @param headerPrevBlockRid is the previous block hash from the header
     * @param headerHeight is the height from the header
     * @param expectedPrevBlockRid is the RID of the previous block we we expect the header to point to
     * @param expectedHeight is the height we we expect to find in the header
     * @param blockRidFromHeight can find the block RID in the DB from what height we have
     * @return the validation result
     */
    fun basicValidationAgainstKnownBlocks(
        headerBlockRid: BlockRid,
        headerPrevBlockRid: BlockRid,
        headerHeight: Long,
        expectedPrevBlockRid: BlockRid,
        expectedHeight: Long,
        blockRidFromHeight: (height: Long) -> ByteArray? // We will probably need to go to DB to find this, so don't call this in vain
    ): ValidationResult {

        return when {
            headerPrevBlockRid != expectedPrevBlockRid ->
                ValidationResult(
                    ValidationResult.Result.PREV_BLOCK_MISMATCH, "header.prevBlockRID != expected previous BlockRID," +
                            "( ${headerPrevBlockRid.toHex()} != ${expectedPrevBlockRid.toHex()} ), " +
                            " height: $headerHeight and $expectedHeight "
                )

            headerHeight > expectedHeight ->
                ValidationResult(
                    ValidationResult.Result.BLOCK_FROM_THE_FUTURE,
                    "Expected height: $expectedHeight, got: $headerHeight, Block RID: ${headerBlockRid.toHex()}"
                )

            headerHeight < expectedHeight -> {
                val bRidByte = blockRidFromHeight(headerHeight)
                if (bRidByte == null) { // This means our DB is broken (shouldn't happen during regular block building)
                    ValidationResult(
                        ValidationResult.Result.OLD_BLOCK_NOT_FOUND,
                        "Got a block at height: $headerHeight while we expect height: "
                                + "$expectedHeight, (even thought we cannot find the locally)."
                    )
                } else {
                    val ourBlockRID = BlockRid(bRidByte!!)
                    if (ourBlockRID == headerBlockRid)
                        ValidationResult(
                            ValidationResult.Result.DUPLICATE_BLOCK,
                            "Duplicate block at height: $headerHeight, Block RID: ${headerBlockRid.toHex()}"
                        )
                    else
                        ValidationResult(
                            ValidationResult.Result.SPLIT,
                            "Blockchain split detected at height $headerHeight. Our block: ${ourBlockRID.toHex()}, " +
                                    "received block: ${headerBlockRid.toHex()}"
                        )
                }
            }

            else -> ValidationResult(ValidationResult.Result.OK)
        }
    }

    /**
     * Validate block header against block data we possess locally. This is extensive and heavy, including:
     *
     * - The checks is [basicValidationAgainstKnownBlocks], and
     * - check that timestamp occurs after previous block's timestamp
     * - check if all required dependencies are present
     * - check for correct root hash
     *
     * @param blockHeader The block header to validate
     * @param initialBlockData is the initial block data we can use to compare with the header
     * @param expectedMerkleRootHash calculates the expected the merkle root of the block
     * @param blockRidFromHeight can find the block RID in the DB from what height we have
     * @param currentBlockTimestamp is the timestamp of the the block we possess
     * @param nrOfDependencies is how many dependencies we should have (we cannot test the actual dependencies b/c some might be null still)
     * @return a [ValidationResult] containing success or info about what went wrong
     */
    fun advancedValidateAgainstKnownBlocks(
        blockHeader: BlockHeader,
        initialBlockData: InitialBlockData,
        expectedMerkleRootHash: () -> ByteArray,
        blockRidFromHeight: (height: Long) -> ByteArray?, // We will probably need to go to DB to find this, so don't call this in vain
        currentBlockTimestamp: Long,
        nrOfDependencies: Int
    ): ValidationResult {
        val header = blockHeader as BaseBlockHeader

        // Pull out some essential info
        val headerBlockRid = BlockRid(header.blockRID)
        val headerPrevBlockRid = BlockRid(header.prevBlockRID)
        val headerHeight = header.blockHeaderRec.getHeight()

        val expectedHeight = initialBlockData.height
        val expectedPrevBlockRid = BlockRid(initialBlockData.prevBlockRID)

        // Do the basic test first
        val basicResult = GenericBlockHeaderValidator.basicValidationAgainstKnownBlocks(headerBlockRid, headerPrevBlockRid, headerHeight, expectedPrevBlockRid, expectedHeight, blockRidFromHeight)
        if (basicResult.result != ValidationResult.Result.OK) {
            return basicResult
        }

        // The "advanced" checks
        return when {
            currentBlockTimestamp >= header.timestamp ->
                ValidationResult(ValidationResult.Result.INVALID_TIMESTAMP, "Block timestamp >= header.timestamp")

            !header.checkCorrectNumberOfDependencies(nrOfDependencies) ->
                ValidationResult(ValidationResult.Result.MISSING_BLOCKCHAIN_DEPENDENCY, "checkIfAllBlockchainDependenciesArePresent() is false")

            !Arrays.equals(header.blockHeaderRec.getMerkleRootHash(), expectedMerkleRootHash()) -> // Do this last since most expensive check!
                ValidationResult(ValidationResult.Result.INVALID_ROOT_HASH, "header.blockHeaderRec.rootHash != computeMerkleRootHash()")

            else -> basicResult // = "OK"
        }
    }


}