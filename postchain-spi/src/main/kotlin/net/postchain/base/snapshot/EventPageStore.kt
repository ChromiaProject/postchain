package net.postchain.base.snapshot

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.core.EContext

open class EventPageStore(
        ctx: EContext,
        levelsPerPage: Int,
        ds: DigestSystem,
        tableNamePrefix: String
) : BasePageStore("${tableNamePrefix}_event", ctx, levelsPerPage, ds) {

    fun writeEventTree(blockHeight: Long, leafHashes: List<Hash>): Hash {
        val entriesPerPage = 1 shl levelsPerPage

        fun updateLevel(level: Int, entryHashes: List<Hash>): Hash {
            var current = 0
            val upperEntry = arrayListOf<Hash>()
            while (current < entryHashes.size) {
                // calculate left boundary of page, in entries on this level
                val left = current - (current % entriesPerPage)

                // retrieve page elements and write a page
                val pageChildren = Array(entriesPerPage) {
                    entryHashes.getOrElse(left + it) { EMPTY_HASH }
                }
                val page = Page(blockHeight, level, left.toLong(), pageChildren)
                writePage(page)

                // calculate page hash for the next level
                val pageHash = page.getChildHash(levelsPerPage, ds::hash, 0)
                upperEntry.add(pageHash)

                // next iteration
                current = left + entriesPerPage
            }
            return if (upperEntry.size > 1)
                updateLevel(level + levelsPerPage, upperEntry)
            else {
                upperEntry[0]
            }
        }

        if (leafHashes.isEmpty()) return EMPTY_HASH
        return updateLevel(0, leafHashes)
    }
}