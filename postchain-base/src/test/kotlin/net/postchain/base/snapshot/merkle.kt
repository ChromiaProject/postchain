package net.postchain.base.snapshot

import net.postchain.common.data.TreeHasher

class TestPageStore(
    override val levelsPerPage: Int,
    override val hasher: TreeHasher
) : BasePageStore {

    private val store = mutableMapOf<Long, SnapshotPage>()
    private var iid: Long = 0

    override fun writeSnapshotPage(page: SnapshotPage) {
        iid++
        store[iid] = page
    }

    override fun readSnapshotPage(blockHeight: Long, level: Int, left: Long): SnapshotPage? {
        for ((_, v) in store) {
            if (v.blockHeight == blockHeight && v.level == level && v.left == left) {
                return v
            }
        }
        return null
    }

    override fun pruneBelowHeight(blockHeight: Long, cleanLeafs: (height: Long) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun highestLevelPage(): Int {
        var highestLevel = 0
        for ((_, v) in store) {
            if (highestLevel < v.level) {
                highestLevel = v.level
            }
        }
        return highestLevel
    }
}