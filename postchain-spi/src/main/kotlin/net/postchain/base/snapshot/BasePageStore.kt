package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext

// base page store can be used for query merkle proof
@Suppress("DuplicatedCode")
open class BasePageStore(
        val name: String,
        val ctx: EContext,
        val levelsPerPage: Int,
        val ds: DigestSystem
) : PageStore {

    override fun writePage(page: Page) {
        val db = DatabaseAccess.of(ctx)
        db.insertPage(ctx, name, page)
    }

    override fun readPage(blockHeight: Long, level: Int, left: Long): Page? {
        val db = DatabaseAccess.of(ctx)
        return db.getPageAtHeight(ctx, name, blockHeight, level, left)
    }

    override fun highestLevelPage(blockHeight: Long): Int {
        val db = DatabaseAccess.of(ctx)
        return db.getHighestLevelPageAtHeight(ctx, name, blockHeight)
    }

    override fun getMerkleProof(blockHeight: Long, leafPos: Long): List<Hash> =
            getMerkleProofForLeafFromLevel(blockHeight, leafPos, 0)

    override fun getRangeMerkleProof(blockHeight: Long, startLeafPos: Long, endLeafPos: Long): RangeProof {
        if (startLeafPos > endLeafPos) throw ProgrammerMistake("startLeaf must be <= endLeaf")
        if (startLeafPos == endLeafPos) {
            return RangeProof(listOf(), listOf(), getMerkleProof(blockHeight, startLeafPos))
        }

        val leftBoundaryHashes = mutableListOf<Hash>()
        val rightBoundaryHashes = mutableListOf<Hash>()

        // Find convergence level - where paths merge
        val convergenceLevel = (startLeafPos xor endLeafPos).takeHighestOneBit().countTrailingZeroBits() + 1

        // Build left and right boundary proofs up to convergence
        for (level in 0 until convergenceLevel step levelsPerPage) {
            val maxRelLevel = minOf(levelsPerPage, convergenceLevel - level)

            // Process left boundary
            leftBoundaryHashes.addAll(
                    processPageLevel(blockHeight, level, startLeafPos, relLevelEnd = maxRelLevel, addPositionToProofPredicate = { it % 2 == 1 })
            )

            // Process right boundary
            rightBoundaryHashes.addAll(
                    processPageLevel(blockHeight, level, endLeafPos, relLevelEnd = maxRelLevel, addPositionToProofPredicate = { it % 2 == 0 })
            )
        }

        // Builds the common path from convergence point to root - same as standard proof
        // We could provide any leaf in the range, but we use endLeaf so we trigger the range check in
        // getMerkleProofForLeafFromLevel
        val commonPath = getMerkleProofForLeafFromLevel(blockHeight, endLeafPos, convergenceLevel)

        return RangeProof(leftBoundaryHashes, rightBoundaryHashes, commonPath)
    }

    /**
     * Gives merkle proof starting from the given level
     */
    private fun getMerkleProofForLeafFromLevel(blockHeight: Long, leafPos: Long, startLevel: Int): List<Hash> {
        val path = mutableListOf<Hash>()
        val highest = highestLevelPage(blockHeight)

        // Sometimes we can actually build a valid proof even though the leaf does not exist in the tree.
        // So we try to accommodate this if possible.
        if (leafPos > 1 shl (highest + levelsPerPage)) {
            throw UserMistake("Can't build a valid proof for Leaf position $leafPos when current highest page level in tree is $highest")
        }

        // Find which page contains the start level
        val pageLevel = (startLevel / levelsPerPage) * levelsPerPage
        val startingRelLevel = startLevel - pageLevel
        var nextPageLevel = pageLevel

        // First iteration: handle the partial page if startLevel is not page-aligned
        if (startingRelLevel > 0) {
            // Convert startNode position from startLevel to pageLevel
            path.addAll(processPageLevel(blockHeight, pageLevel, leafPos, startingRelLevel))
            nextPageLevel = pageLevel + levelsPerPage
        }

        // Continue with the remaining page-aligned levels
        for (level in nextPageLevel..highest step levelsPerPage) {
            path.addAll(
                    processPageLevel(blockHeight, level, leafPos)
            )
        }
        return path
    }

    /**
     * Process and emits proof hashes for a specific level
     *
     * @param blockHeight Height for which proof is generated
     * @param level Level to process
     * @param leafPosition Position of the leaf in the tree
     * @param relLevelStart Relative level from which to start processing (0 means start at page level)
     * @param relLevelEnd Relative level until which to process (levelsPerPage means process until the next page)
     * @param addPositionToProofPredicate Optional predicate that decides whether to add the position to the proof or not
     */
    private fun processPageLevel(
            blockHeight: Long,
            level: Int,
            leafPosition: Long,
            relLevelStart: Int = 0,
            relLevelEnd: Int = levelsPerPage,
            addPositionToProofPredicate: ((Int) -> Boolean)? = null
    ): List<Hash> {
        val hashes = mutableListOf<Hash>()
        val leafsInPage = 1L shl (level + levelsPerPage)
        val left = leafPosition - leafPosition % leafsInPage
        val leftInEntry = left shr level
        val page = readPage(blockHeight, level, leftInEntry)

        var relPos = ((leafPosition - left) shr level).toInt()
        relPos = relPos shr relLevelStart // Adjust position if we start at a non-zero relative level
        for (relLevel in relLevelStart until relLevelEnd) {
            if (addPositionToProofPredicate == null || addPositionToProofPredicate(relPos)) {
                if (page == null) {
                    hashes.add(EMPTY_HASH)
                } else {
                    val another = relPos xor 0x1
                    val hash = page.getChildHash(relLevel, ds::hash, another)
                    hashes.add(hash)
                }
            }
            relPos = relPos shr 1
        }
        return hashes
    }

}
