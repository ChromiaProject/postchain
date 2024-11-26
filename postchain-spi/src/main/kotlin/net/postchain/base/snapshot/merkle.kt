// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.data.TreeHasher
import net.postchain.core.EContext
import java.util.NavigableMap
import java.util.TreeMap

/**
 * Represents a page containing data corresponding to one or several nodes of a Merkle tree.
 *
 * A binary Merkle tree is organized into levels:
 * - **Level 0** corresponds to the leafs of the tree.
 * - **Level 1** corresponds to internal nodes, each of which is logically linked to two leafs.
 * - **Level 2** corresponds to internal nodes, which are linked to two nodes of level 1, and so on.
 *
 * - **Hash of the leaf node**: The hash of the data (normally stored in the `LeafStore`),
 *   but essentially, it is just an opaque byte array from the perspective of the tree
 *   with the same size as the hash.
 * - **Hash of an internal node**: A hash of the concatenation of the hashes of its two children,
 *   as computed by `TreeHasher`.
 *
 * The **EMPTY_HASH** is used to represent missing leafs in a sparse tree. If a node has two
 * EMPTY_HASHes as children, its hash is also EMPTY_HASH. This means that any node with
 * only empty leafs beneath it has an EMPTY_HASH, which makes the sparse tree representation
 * more efficient.
 *
 * To reduce the number of database reads and writes, data is organized into pages. The
 * configurable parameter `levelsPerPage` determines how many levels of the tree are stored in one page.
 *
 * ### Example:
 * For `levelsPerPage = 1`:
 * - A page at level 0 stores 2 leaf nodes, corresponding to 1 intermediate node at level 1.
 *
 * For `levelsPerPage = 2`:
 * - A page at level 0 stores 4 leaf nodes, corresponding to 2 intermediate nodes at level 1,
 *   and thus 1 intermediate node at level 2.
 *
 * The field `left` identifies the position of the page in the tree. It is measured by the number
 * of 'nodes' at the level of the page in the tree.
 *
 * ### Indexing:
 * Visualize the tree as a triangle with `levelsPerPage = 2`:
 * - Levels marked with `[]` are stored explicitly.
 * - Levels marked with `<>` are not explicitly stored but are calculated using `getChildHash`.
 *
 * ```
 * Level 0: [0, 1, 2, 3]  [4, 5, 6, 7]
 * Level 1: <0, 1>  <2, 3>
 * Level 2: [0, 1, 2*, 3*]
 * Level 3: <0, 1*>
 * Level 4: <0>
 * ```
 * In this example:
 * - The page at level 0 with `left = 0` stores leafs 0, 1, 2, and 3.
 * - Level 1 is not explicitly stored but can be computed using `getChildHash` on pages of level 0.
 *   - For example, if we want to access node with index 1 on level 1, we use the page on level 0 with `left = 0`
 *   and supply `relLevel = 1`, and `childIndex = 1`.
 *   - To access node number 3 on level 1, we need to get the level 0 page with `left = 4`,
 *   and give it `relLevel = 1`, and `childIndex = 1`.
 * - On level 2, we have a single page.
 *
 * Entries of the level 2 page can also be computed using `getChildHash`:
 * `N_L2_0 = page(level=0, left=0).getChildHash(relLevel=2, ..., childIndex=0)`
 * `N_L2_1 = page(level=0, left=4).getChildHash(relLevel=2, ..., childIndex=0)`
 *
 * ### Root Hash:
 * - The root hash of the tree is the highest hash that can be computed, such as:
 *   `root_hash = N_L4_0 = page(level=2, left=0).getChildHash(relLevel=2, ..., childIndex=0)`
 *
 * - While `levelsPerPage` does not affect Merkle proof construction, the root of the tree
 *   is considered to be at the level `highestPageLevel + levelsPerPage`.
 * - The size of the tree is always `2 ^ (n * levelsPerPage) = 2 ^ (highestPageLevel + levelsPerPage)`.
 *
 * In the code, page entries correspond to nodes of the tree. The terms "page entry" and "node" are interchangeable,
 * and the above indexing scheme applies to both contexts.
 */
class Page(
        val blockHeight: Long, val level: Int, val left: Long,
        val childHashes: Array<Hash>
) {


    /**
     * Retrieves the hash of a node (not just a "child") within the page using relative coordinates.
     *
     * The name of this function is a misnomer because it provides the hash of the node itself,
     * rather than just a "child." The node is selected using the following relative coordinates:
     *
     * - **relLevel**: The level of the node relative to the level of the page itself.
     * - **childIndex**: The index of the node at that level, within the page.
     *
     * The absolute position of the node is calculated as:
     * `(left shr relLevel) + childIndex`.
     *
     * - If `relLevel = 0`, it returns `childHashes`.
     * - Otherwise, it computes the hash of the node at the specified level above it.
     */
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

open class EventPageStore(
        ctx: EContext,
        levelsPerPage: Int,
        ds: DigestSystem,
        tableNamePrefix: String
) : BasePageStore("${tableNamePrefix}_event", ctx, levelsPerPage, ds) {

    fun writeEventTree(blockHeight: Long, leafHashes: List<Hash>, protocolVersion: Int = 1): Hash {
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
}

