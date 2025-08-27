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

    override fun getMerkleProof(blockHeight: Long, startLeaf: Long, endLeaf: Long): RangeProof {
        if (startLeaf > endLeaf) throw ProgrammerMistake("startLeaf must be <= endLeaf")
        if (startLeaf == endLeaf) {
            return RangeProof(listOf(), listOf(), getMerkleProof(blockHeight, startLeaf))
        }

        val leftBoundaryHashes = mutableListOf<Hash>()
        val rightBoundaryHashes = mutableListOf<Hash>()

        // Find convergence level - where paths merge
        var convergenceLevel = 0
        var tempStart = startLeaf
        var tempEnd = endLeaf

        while (tempStart != tempEnd) {
            tempStart = tempStart shr 1
            tempEnd = tempEnd shr 1
            convergenceLevel++
        }

        // Build left and right boundary proofs up to convergence
        for (level in 0 until convergenceLevel step levelsPerPage) {
            val actualLevelsInPage = minOf(levelsPerPage, convergenceLevel - level)
            val leafsInPage = 1L shl (level + levelsPerPage)

            // Process left boundary
            val leftPageLeft = startLeaf - startLeaf % leafsInPage
            val leftInEntry = leftPageLeft shr level
            val leftPage = readPage(blockHeight, level, leftInEntry)

            var leftRelPos = ((startLeaf - leftPageLeft) shr level).toInt()
            for (relLevel in 0 until actualLevelsInPage) {
                if (leftRelPos % 2 == 1) { // We're the right child, sibling is on the left
                    val sibling = leftRelPos xor 1
                    val hash = leftPage?.getChildHash(relLevel, ds::hash, sibling) ?: EMPTY_HASH
                    leftBoundaryHashes.add(hash)
                }
                leftRelPos = leftRelPos shr 1
            }

            // Process right boundary
            val rightPageLeft = endLeaf - endLeaf % leafsInPage
            val leftInRightEntry = rightPageLeft shr level
            val rightPage = readPage(blockHeight, level, leftInRightEntry)

            var rightRelPos = ((endLeaf - rightPageLeft) shr level).toInt()
            for (relLevel in 0 until actualLevelsInPage) {
                if (rightRelPos % 2 == 0) { // We're the left child, sibling is on the right
                    val sibling = rightRelPos xor 1
                    val hash = rightPage?.getChildHash(relLevel, ds::hash, sibling) ?: EMPTY_HASH
                    rightBoundaryHashes.add(hash)
                }
                rightRelPos = rightRelPos shr 1
            }
        }

        // Builds the common path from convergence point to root - same as standard proof
        // We could provide any leaf in the range, but we use endLeaf so we trigger the range check in
        // getMerkleProofForLeafFromLevel
        val commonPath = getMerkleProofForLeafFromLevel(blockHeight, endLeaf, convergenceLevel)

        return RangeProof(leftBoundaryHashes, rightBoundaryHashes, commonPath)
    }

    /**
     * Gives merkle proof starting from the given level
     */
    private fun getMerkleProofForLeafFromLevel(blockHeight: Long, leaf: Long, startLevel: Int): List<Hash> {
        val path = mutableListOf<Hash>()
        val highest = highestLevelPage(blockHeight)

        // Sometimes we can actually build a valid proof even though the leaf does not exist in the tree.
        // So we try to accommodate this if possible.
        if (leaf > 1 shl (highest + levelsPerPage)) {
            throw UserMistake("Can't build a valid proof for Leaf position $leaf when current highest page level in tree is $highest")
        }

        // Find which page contains the start level
        val pageLevel = (startLevel / levelsPerPage) * levelsPerPage
        val startingRelLevel = startLevel - pageLevel

        var nextPageLevel = pageLevel

        // First iteration: handle the partial page if startLevel is not page-aligned
        if (startingRelLevel > 0) {
            // Convert startNode position from startLevel to pageLevel
            val leafsInPage = 1L shl (pageLevel + levelsPerPage)
            val left = leaf - (leaf % leafsInPage)
            val leftInEntry = left shr pageLevel
            val page = readPage(blockHeight, pageLevel, leftInEntry)

            if (page == null) {
                repeat(levelsPerPage - startingRelLevel) { path.add(EMPTY_HASH) }
            } else {
                var relPos = ((leaf - left) shr pageLevel).toInt()
                // Shift to the starting level within the page
                relPos = relPos shr startingRelLevel

                // Start from the startingRelLevel within the page
                for (relLevel in startingRelLevel until levelsPerPage) {
                    val another = relPos xor 0x1
                    val hash = page.getChildHash(relLevel, ds::hash, another)
                    path.add(hash)
                    relPos = relPos shr 1
                }
            }

            nextPageLevel = pageLevel + levelsPerPage
        }

        // Continue with the remaining page-aligned levels
        for (level in nextPageLevel..highest step levelsPerPage) {
            val leafsInPage = 1L shl (level + levelsPerPage)
            val left = leaf - leaf % leafsInPage
            val leftInEntry = left shr level
            val page = readPage(blockHeight, level, leftInEntry)
            if (page == null) {
                repeat(levelsPerPage) { path.add(EMPTY_HASH) }
                continue
            }
            var relPos = ((leaf - left) shr level).toInt() // relative position of entry on a level
            for (relLevel in 0 until levelsPerPage) {
                val another = relPos xor 0x1 // flip the lowest bit to find the other child of the same node
                val hash = page.getChildHash(relLevel, ds::hash, another)
                path.add(hash)
                relPos = relPos shr 1
            }
        }
        return path
    }
}
