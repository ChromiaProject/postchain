// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.core.ProgrammerMistake
import java.util.*
import javax.sql.DataSource

typealias Hash = ByteArray

typealias TreeHasher = (Hash, Hash) -> Hash;

class SnapshotPage(val blockHeight: Long, val level: Int, val left: Long,
                   val childHashes: Array<Hash>) {
    fun getHashes(relLevel: Int, treeHasher: TreeHasher): Array<Hash> {
        if (relLevel == 0) return childHashes
        else {
            val priorHashes = getHashes(relLevel - 1, treeHasher)
            return Array<Hash>(
                    (priorHashes.size / 2)
            ) { i ->
                treeHasher(priorHashes[i * 2], priorHashes[i * 2 + 1])
            }
        }
    }
}

// TODO: do we need to suppply blockEContext as parameters?
abstract class SnapshotPageStore(
        val levelsPerPage: Int,
        val writeDataSource: DataSource,
        val chainID: Long,
        val snapshotName: String,
        val hasher: TreeHasher
) {
    abstract fun writeSnapshotPage(page: SnapshotPage)

    // read the page with blockHeight equal or lower than given
    // for a given level and position, if exists
    abstract fun readSnapshotPage(blockHeight: Long, level: Int, left: Long): SnapshotPage?

    // delete all pages with height at or below given
    // except those which are used
    abstract fun pruneBelowHeight(blockHeight: Long, cleanLeafs: (height: Long) -> Unit)

    abstract fun highestLevelPage(): Int
}

val EMPTY_HASH = ByteArray(32, { 0 })

fun getMerkleProof(blockHeight: Long, store: SnapshotPageStore, leafPos: Long): List<Hash> {
    val path = mutableListOf<Hash>()
    val highest = store.highestLevelPage()
    for (level in 0..highest step store.levelsPerPage) {
        val leafsInPage = 1 shl (level + store.levelsPerPage)
        val left = leafPos - leafPos % leafsInPage
        val page = store.readSnapshotPage(blockHeight, level, left)!!
        var relPos = ((leafPos - left) shr level).toInt() // relative position of entry on a level
        // TODO: number of levels can be different for the topmost page
        for (relLevel in 0 until store.levelsPerPage) {
            val another = relPos xor 0x1 // flip the lowest bit to find the other child of same node
            val hashes = page.getHashes(relLevel, store.hasher) // TODO: this is inefficient
            path.add(hashes[another])
            relPos = relPos shr 1
        }
    }
    return path
}

fun updateSnapshot(store: SnapshotPageStore, blockHeight: Long, leafHashes: NavigableMap<Long, Hash>): Hash {
    val entriesPerPage = 1 shl store.levelsPerPage
    val prevHighestLevelPage = store.highestLevelPage()

    fun updateLevel(level: Int, entryHashes: NavigableMap<Long, Hash>): Hash {
        var current = 0L
        val upperEntryMap = TreeMap<Long, Hash>()
        val leafsPerEntry = 1 shl level // one entry corresponds to this many leafs at the bottom
        while (true) {
            val next = entryHashes.ceilingEntry(current) ?: break
            // calculate left boundary of page, in entries on this level, in entries at this level
            val leftInEntries = next.key - (next.key % entriesPerPage)
            val left = leafsPerEntry * leftInEntries // left in leafs
            var haveMissingLeafs = false
            val pageElts = Array(entriesPerPage) {
                val leaf = entryHashes.get(it + left)
                if (leaf == null) haveMissingLeafs = true
                leaf
            }
            if (haveMissingLeafs) {
                val oldPage = store.readSnapshotPage(blockHeight, level, left)
                if (oldPage != null) {
                    for (i in 0 until entriesPerPage) {
                        if (pageElts[i] == null)
                            pageElts[i] = oldPage.childHashes[i]
                    }
                }
            }
            val pageChildren = pageElts.map { if (it == null) EMPTY_HASH else it }.toTypedArray()
            val page = SnapshotPage(blockHeight, level, left, pageChildren)
            val pageHash = page.getHashes(store.levelsPerPage, store.hasher)[0]
            upperEntryMap[leftInEntries / entriesPerPage] = pageHash
            store.writeSnapshotPage(page)
            current = leftInEntries + entriesPerPage
        }
        if (upperEntryMap.lastKey() > 0 || prevHighestLevelPage > level)
            return updateLevel(level + store.levelsPerPage, upperEntryMap)
        else
            return upperEntryMap[0]!!
    }

    if (leafHashes.size == 0) throw ProgrammerMistake("couldn't because")
    return updateLevel(0, leafHashes)
}

