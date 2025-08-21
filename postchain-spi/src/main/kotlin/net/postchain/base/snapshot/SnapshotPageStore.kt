// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.core.EContext
import java.util.NavigableMap
import java.util.TreeMap

@Suppress("FunctionName", "DuplicatedCode")
open class SnapshotPageStore(
        ctx: EContext,
        levelsPerPage: Int,
        val snapshotsToKeep: Int,
        ds: DigestSystem,
        private val tableNamePrefix: String
) : BasePageStore("${tableNamePrefix}_snapshot", ctx, levelsPerPage, ds) {

    // Read the page with blockHeight equal or lower than given
    // for a given level and position, if exists.
    override fun readPage(blockHeight: Long, level: Int, left: Long): Page? {
        val db = DatabaseAccess.of(ctx)
        return db.getPageEqualOrLowerThanHeight(ctx, name, blockHeight, level, left)
    }

    override fun highestLevelPage(blockHeight: Long): Int {
        val db = DatabaseAccess.of(ctx)
        return db.getHighestLevelPageEqualOrLowerThanHeight(ctx, name, blockHeight)
    }

    fun updateSnapshot(blockHeight: Long, leafHashes: NavigableMap<Long, Hash>, protocolVersion: Int = 1): Hash {
        return when (protocolVersion) {
            1 -> _updateSnapshotV1(blockHeight, leafHashes)
            else -> _updateSnapshotV2(blockHeight, leafHashes)
        }
    }

    private fun _updateSnapshotV1(blockHeight: Long, leafHashes: NavigableMap<Long, Hash>): Hash {
        val entriesPerPage = 1 shl levelsPerPage
        val prevHighestLevelPage = highestLevelPage(blockHeight - 1)

        if (leafHashes.size == 0) {
            return EMPTY_HASH
        }

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

        return updateLevel(0, leafHashes)
    }

    private fun _updateSnapshotV2(blockHeight: Long, leafHashes: NavigableMap<Long, Hash>): Hash {
        val entriesPerPage = 1 shl levelsPerPage
        val prevHighestLevelPage = highestLevelPage(blockHeight - 1)

        if (leafHashes.size == 0) {
            val page = readPage(blockHeight, prevHighestLevelPage, 0)
            return page?.getChildHash(levelsPerPage, ds::hash, 0) ?: EMPTY_HASH
        }

        fun updateLevel(level: Int, entryHashes: NavigableMap<Long, Hash>): Hash {
            var current = 0L
            val upperEntryMap = TreeMap<Long, Hash>() // entries at the (level + levelsPerPage) level
            while (true) {
                val next = entryHashes.ceilingEntry(current) ?: break

                // calculate left boundary of page, in entries on this level
                val left = next.key - (next.key % entriesPerPage)

                // retrieve page elements and write a page
                var missingElements = false
                val pageElements = Array(entriesPerPage) {
                    val leaf = entryHashes[left + it]
                    if (leaf == null) missingElements = true
                    leaf
                }
                if (missingElements) {
                    val oldPage = readPage(blockHeight, level, left)
                    if (oldPage != null) {
                        for (i in pageElements.indices) {
                            pageElements[i] = pageElements[i] ?: oldPage.childHashes[i]
                        }
                    }
                }
                val pageChildren = pageElements.map { it ?: EMPTY_HASH }.toTypedArray()
                val page = Page(blockHeight, level, left, pageChildren)
                writePage(page)

                // calculate page hash for the next level
                // (left / entriesPerPage) is the left boundary of the page at the next level
                // it is same as (left shr levelsPerPage)
                // always an integer given that left is a multiple of entriesPerPage
                val pageHash = page.getChildHash(levelsPerPage, ds::hash, 0)
                upperEntryMap[left / entriesPerPage] = pageHash

                // next iteration
                current = left + entriesPerPage
            }
            return if (upperEntryMap.lastKey() > 0 || prevHighestLevelPage > level) {
                if (prevHighestLevelPage == level) {
                    // once we reach this level we need to make sure we include the
                    // existing highest page in the tree
                    if (upperEntryMap.firstKey() > 0) {
                        // all the updated entries are to the right side of the already populated side
                        // of the tree, thus we need to add existing highest page
                        val highestPage = readPage(blockHeight, level, 0)
                        if (highestPage != null) {
                            upperEntryMap[0] = highestPage.getChildHash(levelsPerPage, ds::hash, 0)
                        }
                    }
                }
                updateLevel(level + levelsPerPage, upperEntryMap)
            } else {
                // once we got to the root of the tree there's no need to write a new
                // page, as root hash can be computed from the current level
                upperEntryMap[0]!!
            }
        }

        return updateLevel(0, leafHashes)
    }

    /**
     * Delete all pages of the snapshot that are older than the given block height
     * except those which are still in used
     */
    open fun pruneSnapshot(blockHeight: Long) {
        if (snapshotsToKeep < 1) return

        val db = DatabaseAccess.of(ctx)
        val lowestHeightToKeep = db.getLowestSnapshotHeightToKeep(ctx, name, blockHeight, snapshotsToKeep)
                ?: return
        val pageIIDs = db.getPrunablePages(ctx, name, lowestHeightToKeep)
        if (pageIIDs.isNotEmpty()) {
            val leftIndexes = db.getLeftIndex(ctx, name, pageIIDs)
            leftIndexes.forEach {
                db.safePruneAccountStates(
                        ctx, tableNamePrefix,
                        it, it + (1 shl levelsPerPage) - 1,
                        lowestHeightToKeep
                )
            }
            db.deletePages(ctx, name, pageIIDs)
        }
    }

    fun getRootHashAtHeight(blockHeight: Long): Hash {
        val prevHighestLevelPage = highestLevelPage(blockHeight - 1)
        val page = readPage(blockHeight, prevHighestLevelPage, 0)
        return page?.getChildHash(levelsPerPage, ds::hash, 0) ?: EMPTY_HASH
    }

    fun getLastSnapshotHeight(): Long? = DatabaseAccess.of(ctx).getLatestSnapshotHeight(ctx, name)
}

