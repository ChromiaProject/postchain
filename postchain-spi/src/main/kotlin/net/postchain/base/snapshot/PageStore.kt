package net.postchain.base.snapshot

import net.postchain.common.data.Hash

interface PageStore {
    fun writePage(page: Page)
    fun readPage(blockHeight: Long, level: Int, left: Long): Page?
    fun highestLevelPage(blockHeight: Long): Int
    fun getMerkleProof(blockHeight: Long, leafPos: Long): List<Hash>
}