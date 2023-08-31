// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.data.TreeHasher
import net.postchain.core.EContext
import java.util.NavigableMap
import java.util.TreeMap

class Page(
        val blockHeight: Long, val level: Int, val left: Long,
        val childHashes: Array<Hash>
) {
    fun getChildHash(relLevel: Int, treeHasher: TreeHasher, childIndex: Int): Hash {
        return if (relLevel == 0) childHashes[childIndex]
        else {
            val leftHash = getChildHash(relLevel - 1, treeHasher, 2 * childIndex)
            val rightHash = getChildHash(relLevel - 1, treeHasher, 2 * childIndex + 1)
            treeHasher(leftHash, rightHash)
        }
    }
}

interface PageStore {
    fun writePage(page: Page)
    fun readPage(blockHeight: Long, level: Int, left: Long): Page?
    fun highestLevelPage(blockHeight: Long): Int
    fun getMerkleProof(blockHeight: Long, leafPos: Long): List<Hash>
}

// base page store can be used for query merkle proof
open class BasePageStore(
        val name: String,
        val ctx: EContext,
        val levelsPerPage: Int,
        val ds: DigestSystem) : PageStore {

    override fun writePage(page: Page) {
        val db = DatabaseAccess.of(ctx)
        db.insertPage(ctx, name, page)
    }

    // read the page with blockHeight equal or lower than given
    // for a given level and position, if exists
    override fun readPage(blockHeight: Long, level: Int, left: Long): Page? {
        val db = DatabaseAccess.of(ctx)
        return db.getPage(ctx, name, blockHeight, level, left)
    }

    override fun highestLevelPage(blockHeight: Long): Int {
        val db = DatabaseAccess.of(ctx)
        return db.getHighestLevelPage(ctx, name, blockHeight)
    }

    override fun getMerkleProof(blockHeight: Long, leafPos: Long): List<Hash> {
        val path = mutableListOf<Hash>()
        val highest = highestLevelPage(blockHeight)
        for (level in 0..highest step levelsPerPage) {
            val leafsInPage = 1 shl (level + levelsPerPage)
            val left = leafPos - leafPos % leafsInPage
            val leftInEntry = left / (1 shl level)
            // if page is not found (a.k.a null), continue move to handle next page
            val page = readPage(blockHeight, level, leftInEntry) ?: continue
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

open class EventPageStore(
        ctx: EContext,
        levelsPerPage: Int,
        ds: DigestSystem,
        tableNamePrefix: String
) : BasePageStore("${tableNamePrefix}_event", ctx, levelsPerPage, ds) {

    fun writeEventTree(blockHeight: Long, leafHashes: List<Hash>): Hash {
        val entriesPerPage = 1 shl levelsPerPage
        val prevHighestLevelPage = highestLevelPage(blockHeight)

        fun updateLevel(level: Int, entryHashes: List<Hash>): Hash {
            var current = 0
            val upperEntry = arrayListOf<Hash>()
            while (current < entryHashes.size) {
                // calculate left boundary of page, in entries on this level
                val left = current - (current % entriesPerPage)
                val pageChildren = Array(entriesPerPage) {
                    entryHashes.getOrElse(it + left) { EMPTY_HASH }
                }
                val page = Page(blockHeight, level, left.toLong(), pageChildren)
                val pageHash = page.getChildHash(levelsPerPage, ds::hash, 0)
                upperEntry.add(pageHash)
                writePage(page)
                current = left + entriesPerPage
            }
            return if (upperEntry.size > 2 || prevHighestLevelPage > level)
                updateLevel(level + levelsPerPage, upperEntry)
            else {
                upperEntry[0]
            }
        }

        if (leafHashes.isEmpty()) return EMPTY_HASH
        return updateLevel(0, leafHashes)
    }
}

open class SnapshotPageStore(
        ctx: EContext,
        levelsPerPage: Int,
        val snapshotsToKeep: Int,
        ds: DigestSystem,
        private val tableNamePrefix: String
) : BasePageStore("${tableNamePrefix}_snapshot", ctx, levelsPerPage, ds) {

    fun updateSnapshot(blockHeight: Long, leafHashes: NavigableMap<Long, Hash>): Hash {
        val entriesPerPage = 1 shl levelsPerPage
        val prevHighestLevelPage = highestLevelPage(blockHeight - 1)

        fun updateLevel(level: Int, entryHashes: NavigableMap<Long, Hash>): Hash {
            var current = 0L
            val upperEntryMap = TreeMap<Long, Hash>()
            while (true) {
                val next = entryHashes.ceilingEntry(current) ?: break
                // calculate left boundary of page, in entries on this level
                val left = next.key - (next.key % entriesPerPage)
                var haveMissingLeafs = false
                val pageElts = Array(entriesPerPage) {
                    val leaf = entryHashes[it + left]
                    if (leaf == null) haveMissingLeafs = true
                    leaf
                }
                if (haveMissingLeafs) {
                    val oldPage = readPage(blockHeight, level, left)
                    if (oldPage == null) {
                        // calculate the topmost page from all existing lower level pages
                        val lowerLevel = level shr 1
                        val mostLeft = level * left
                        for (i in 0 until entriesPerPage) {
                            val childPage = readPage(blockHeight, lowerLevel, mostLeft + i)
                            if (childPage != null && pageElts[i] == null)
                                pageElts[i] = childPage.getChildHash(lowerLevel, ds::hash, 0)
                        }
                    } else {
                        for (i in 0 until entriesPerPage) {
                            if (pageElts[i] == null)
                                pageElts[i] = oldPage.childHashes[i]
                        }
                    }
                }
                val pageChildren = pageElts.map { it ?: EMPTY_HASH }.toTypedArray()
                val page = Page(blockHeight, level, left, pageChildren)
                val pageHash = page.getChildHash(levelsPerPage, ds::hash, 0)
                upperEntryMap[left / entriesPerPage] = pageHash
                writePage(page)
                current = left + entriesPerPage
            }
            return if (upperEntryMap.lastKey() > 0 || prevHighestLevelPage > level)
                updateLevel(level + levelsPerPage, upperEntryMap)
            else {
                upperEntryMap[0]!!
            }
        }

        if (leafHashes.size == 0) return EMPTY_HASH
        return updateLevel(0, leafHashes)
    }

    /**
     * Delete all pages of the snapshot that are older than the given block height
     */
    open fun pruneSnapshot(blockHeight: Long) {
        val db = DatabaseAccess.of(ctx)
        val prunableSnapshotHeights = db.getPrunableSnapshotHeight(ctx, name, blockHeight, snapshotsToKeep)
                ?: return
        val pageIids = db.getPrunablePages(ctx, name, prunableSnapshotHeights.first, prunableSnapshotHeights.second)
        val leftIndex = db.getLeftIndex(ctx, name, pageIids)
        leftIndex.forEach {
            db.safePruneAccountStates(ctx, tableNamePrefix, it + 1, it + (1 shl levelsPerPage),
                    prunableSnapshotHeights.first, prunableSnapshotHeights.second)
        }
        db.deletePages(ctx, name, pageIids)
    }
}

