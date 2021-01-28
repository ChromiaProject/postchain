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
        var maxHeight: Long = 0
        var result: SnapshotPage? = null
        for ((_, v) in store) {
            if (v.blockHeight <= blockHeight && v.level == level && v.left == left) {
                if (maxHeight < v.blockHeight) {
                    maxHeight = v.blockHeight
                    result = v
                }
            }
        }
        return result
    }

    override fun pruneBelowHeight(blockHeight: Long, cleanLeafs: (height: Long) -> Unit) {
        TODO("Not yet implemented")
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