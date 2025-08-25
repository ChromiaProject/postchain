package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
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

    override fun getMerkleProof(blockHeight: Long, leafPos: Long): List<Hash> {
        val path = mutableListOf<Hash>()
        val highest = highestLevelPage(blockHeight)
        for (level in 0..highest step levelsPerPage) {
            val leafsInPage = 1 shl (level + levelsPerPage)
            val left = leafPos - leafPos % leafsInPage
            val leftInEntry = left shr level
            val page = readPage(blockHeight, level, leftInEntry)
            if (page == null) {
                repeat(levelsPerPage) { path.add(EMPTY_HASH) }
                continue
            }
            var relPos = ((leafPos - left) shr level).toInt() // relative position of entry on a level
            for (relLevel in 0 until levelsPerPage) {
                val another = relPos xor 0x1 // flip the lowest bit to find the other child of same node
                val hash = page.getChildHash(relLevel, ds::hash, another)
                path.add(hash)
                relPos = relPos shr 1
            }
        }
        return path
    }

    override fun getMerkleProof(blockHeight: Long, startLeaf: Long, endLeaf: Long): RangeProof {
        if (startLeaf > endLeaf) throw ProgrammerMistake("startLeaf must be <= endLeaf")
        if (startLeaf == endLeaf) {
            return RangeProof(listOf(), listOf(), getMerkleProof(blockHeight, startLeaf))
        }

        val leftBoundaryHashes = mutableListOf<Hash>()
        val rightBoundaryHashes = mutableListOf<Hash>()
        val commonPath = mutableListOf<Hash>()

        val highest = highestLevelPage(blockHeight)

        var currentStart = startLeaf
        var currentEnd = endLeaf

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

            // Process left boundary
            val leftLeafsInPage = 1L shl (level + actualLevelsInPage)
            val leftPageLeft = currentStart - (currentStart % leftLeafsInPage)
            val leftPage = readPage(blockHeight, level, leftPageLeft shr level)

            var leftRelPos = (currentStart - leftPageLeft).toInt()
            for (relLevel in 0 until actualLevelsInPage) {
                if (leftRelPos % 2 == 1) { // We're the right child, sibling is on the left
                    val sibling = leftRelPos xor 1
                    val hash = leftPage?.getChildHash(relLevel, ds::hash, sibling) ?: EMPTY_HASH
                    leftBoundaryHashes.add(hash)
                }
                leftRelPos = leftRelPos shr 1
            }

            // Process right boundary (if different from left)
            if (currentStart != currentEnd) {
                val rightLeafsInPage = 1L shl (level + actualLevelsInPage)
                val rightPageLeft = currentEnd - (currentEnd % rightLeafsInPage)
                val rightPage = readPage(blockHeight, level, rightPageLeft shr level)

                var rightRelPos = (currentEnd - rightPageLeft).toInt()
                for (relLevel in 0 until actualLevelsInPage) {
                    if (rightRelPos % 2 == 0) { // We're the left child, sibling is on the right
                        val sibling = rightRelPos xor 1
                        val hash = rightPage?.getChildHash(relLevel, ds::hash, sibling) ?: EMPTY_HASH
                        rightBoundaryHashes.add(hash)
                    }
                    rightRelPos = rightRelPos shr 1
                }
            }

            currentStart = currentStart ushr actualLevelsInPage
            currentEnd = currentEnd ushr actualLevelsInPage
        }

        // Build common path from convergence point to root - same as standard proof
        var commonNode = currentStart // At convergence, start == end
        for (level in convergenceLevel..highest step levelsPerPage) {
            val leafsInPage = 1L shl (level + levelsPerPage)
            val pageLeft = commonNode - (commonNode % leafsInPage)
            val page = readPage(blockHeight, level, pageLeft shr level)

            var relPos = (commonNode - pageLeft).toInt()

            for (relLevel in 0 until levelsPerPage) {
                val sibling = relPos xor 1
                val hash = page?.getChildHash(relLevel, ds::hash, sibling) ?: EMPTY_HASH
                commonPath.add(hash)
                relPos = relPos shr 1
            }

            commonNode = commonNode shr levelsPerPage
        }

        return RangeProof(leftBoundaryHashes, rightBoundaryHashes, commonPath)
    }
}
