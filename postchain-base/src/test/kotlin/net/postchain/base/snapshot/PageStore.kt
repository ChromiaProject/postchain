// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.base.snapshot.DigestSystem
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.Page
import net.postchain.base.snapshot.SnapshotPageStore

class TestSnapshotPageStore(
    levelsPerPage: Int,
    ds: DigestSystem
) : SnapshotPageStore(null, levelsPerPage, ds) {

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

class TestEventPageStore(
    levelsPerPage: Int,
    ds: DigestSystem
) : EventPageStore(null, levelsPerPage, ds) {

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