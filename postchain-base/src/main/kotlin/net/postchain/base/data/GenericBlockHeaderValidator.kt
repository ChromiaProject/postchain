package net.postchain.base.data

import net.postchain.base.BaseBlockHeader
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockRid
import net.postchain.core.ValidationResult
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.InitialBlockData
import net.postchain.gtv.Gtv

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
            headerPrevBlockRid: BlockRid?, // Might be missing (for first block)
            headerHeight: Long,
            expectedPrevBlockRid: BlockRid?, // Might be missing (for first block)
            expectedHeight: Long,
            blockRidFromHeight: (height: Long) -> ByteArray? // We will probably need to go to DB to find this, so don't call this in vain
    ): ValidationResult {

        return when {
            headerPrevBlockRid == null && expectedPrevBlockRid != null ->
                ValidationResult(
                        ValidationResult.Result.PREV_BLOCK_MISMATCH, "header.prevBlockRID doesn't exist, while we " +
                        "expected previous BlockRID = ${expectedPrevBlockRid.toHex()} , " +
                        " height: $headerHeight and $expectedHeight "
                )

            expectedPrevBlockRid == null && headerPrevBlockRid != null ->
                ValidationResult(
                        ValidationResult.Result.PREV_BLOCK_MISMATCH, "We expected null but got header.prevBlockRID " +
                        "= ${headerPrevBlockRid.toHex()} height: $headerHeight and $expectedHeight "
                )

            headerPrevBlockRid!! != expectedPrevBlockRid!! ->
                ValidationResult(
                        ValidationResult.Result.PREV_BLOCK_MISMATCH, "header.prevBlockRID != expected previous " +
                        "BlockRID,( ${headerPrevBlockRid.toHex()} != ${expectedPrevBlockRid.toHex()} ), " +
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
                    val ourBlockRID = BlockRid(bRidByte)
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
     * Same as above, but we must validate an entire group of headers
     */
    fun multiValidationAgainstKnownBlocks(
            bcRid: BlockchainRid,
            headerMap: Map<Long, MinimalBlockHeaderInfo>, // headerHeight: Long -> Minimal header
            prevMinimalHeader: MinimalBlockHeaderInfo?,
            blockRidFromHeight: (height: Long) -> ByteArray? // We will probably need to go to DB to find this, so don't call this in vain
    ): ValidationResult {

        // Go through them in order and check for gaps
        var expHeight: Long = 0
        var expPrevBlockRid: BlockRid? = BlockRid(bcRid.data) // If we don't have a previous block we'll use the Blockchain RID
        if (prevMinimalHeader != null) {
            expHeight = prevMinimalHeader.headerHeight + 1
            expPrevBlockRid = prevMinimalHeader.headerBlockRid
        }
        for (height in headerMap.keys) {

            val minimalHeader = headerMap[height]!!
            val res = basicValidationAgainstKnownBlocks(minimalHeader.headerBlockRid, minimalHeader.headerPrevBlockRid, height, expPrevBlockRid, expHeight, blockRidFromHeight)
            if (res.result != ValidationResult.Result.OK) {
                // Failed so we can abort here
                return res
            }

            expHeight++
            expPrevBlockRid = minimalHeader.headerBlockRid
        }

        return ValidationResult(ValidationResult.Result.OK)
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
     * @param currentBlockTimestamp is the timestamp of the block we possess
     * @param nrOfDependencies is how many dependencies we should have (we cannot test the actual dependencies b/c some might be null still)
     * @param extraData header extra data
     * @return a [ValidationResult] containing success or info about what went wrong
     */
    fun advancedValidateAgainstKnownBlocks(
            blockHeader: BlockHeader,
            initialBlockData: InitialBlockData,
            expectedMerkleRootHash: () -> ByteArray,
            blockRidFromHeight: (height: Long) -> ByteArray?, // We will probably need to go to DB to find this, so don't call this in vain
            currentBlockTimestamp: Long,
            nrOfDependencies: Int,
            extraData: Map<String, Gtv>
    ): ValidationResult {
        val header = blockHeader as BaseBlockHeader

        // Pull out some essential info
        val headerBlockRid = BlockRid(header.blockRID)
        val headerPrevBlockRid = BlockRid(header.prevBlockRID)
        val headerHeight = header.blockHeaderRec.getHeight()

        val expectedHeight = initialBlockData.height
        val expectedPrevBlockRid = BlockRid(initialBlockData.prevBlockRID)

        // Do the basic test first
        val basicResult = basicValidationAgainstKnownBlocks(headerBlockRid, headerPrevBlockRid, headerHeight, expectedPrevBlockRid, expectedHeight, blockRidFromHeight)
        if (basicResult.result != ValidationResult.Result.OK) {
            return basicResult
        }

        // The "advanced" checks
        return when {
            currentBlockTimestamp >= header.timestamp ->
                ValidationResult(ValidationResult.Result.INVALID_TIMESTAMP, "Block timestamp >= header.timestamp")

            !header.checkCorrectNumberOfDependencies(nrOfDependencies) ->
                ValidationResult(ValidationResult.Result.MISSING_BLOCKCHAIN_DEPENDENCY, "checkIfAllBlockchainDependenciesArePresent() is false")

            !header.blockHeaderRec.getMerkleRootHash().contentEquals(expectedMerkleRootHash()) -> // Do this last since most expensive check!
                ValidationResult(ValidationResult.Result.INVALID_ROOT_HASH, "header.blockHeaderRec.rootHash != computeMerkleRootHash()")

            !header.checkExtraData(extraData) ->
                ValidationResult(ValidationResult.Result.INVALID_EXTRA_DATA, "header extra data is not match")

            else -> basicResult // = "OK"
        }
    }


}