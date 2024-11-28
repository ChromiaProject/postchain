package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.core.EContext

// base page store can be used for query merkle proof
@Suppress("DuplicatedCode", "FunctionName")
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
}