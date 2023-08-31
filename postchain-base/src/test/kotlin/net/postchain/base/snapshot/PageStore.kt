// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.core.EContext

class TestSnapshotPageStore(
    ctx: EContext,
    levelsPerPage: Int,
    snapshotsToKeep: Int,
    ds: DigestSystem
) : SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, "test") {

    private val store = mutableMapOf<Long, Page>()
    private var iid: Long = 0

    override fun writePage(page: Page) {
        iid++
        store[iid] = page
    }

    override fun readPage(blockHeight: Long, level: Int, left: Long): Page? {
        var maxHeight: Long = 0
        var result: Page? = null
        for ((_, v) in store) {
            if (v.blockHeight <= blockHeight && v.level == level && v.left == left) {
                if (maxHeight <= v.blockHeight) {
                    maxHeight = v.blockHeight
                    result = v
                }
            }
        }
        return result
    }

    override fun highestLevelPage(blockHeight: Long): Int {
        var highestLevel = 0
        for ((_, v) in store) {
            if (highestLevel < v.level && v.blockHeight <= blockHeight) {
                highestLevel = v.level
            }
        }
        return highestLevel
    }

    override fun pruneSnapshot(blockHeight: Long) {
        val iter = store.iterator()
        while (iter.hasNext()) {
            val (_, v) = iter.next()
            if (v.blockHeight <= blockHeight - snapshotsToKeep) {
                iter.remove()
            }
        }
    }
}

class TestEventPageStore(
    ctx: EContext,
    levelsPerPage: Int,
    ds: DigestSystem
) : EventPageStore(ctx, levelsPerPage, ds, "test") {

    private val store = mutableMapOf<Long, Page>()
    private var iid: Long = 0

    override fun writePage(page: Page) {
        iid++
        store[iid] = page
    }

    override fun readPage(blockHeight: Long, level: Int, left: Long): Page? {
        var maxHeight: Long = 0
        var result: Page? = null
        for ((_, v) in store) {
            if (v.blockHeight <= blockHeight && v.level == level && v.left == left) {
                if (maxHeight <= v.blockHeight) {
                    maxHeight = v.blockHeight
                    result = v
                }
            }
        }
        return result
    }

    override fun highestLevelPage(blockHeight: Long): Int {
        var highestLevel = 0
        for ((_, v) in store) {
            if (highestLevel < v.level && v.blockHeight <= blockHeight) {
                highestLevel = v.level
            }
        }
        return highestLevel
    }
}