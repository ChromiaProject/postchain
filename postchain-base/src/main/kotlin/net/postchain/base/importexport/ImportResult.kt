package net.postchain.base.importexport

import net.postchain.common.BlockchainRid

data class ImportResult(
        val fromHeight: Long,
        val toHeight: Long,
        val lastSkippedBlock: Long,
        val firstImportedBlock: Long,
        val numBlocks: Long,
        val blockchainRid: BlockchainRid
) {
    override fun toString(): String {
        val subs = mutableListOf<String>()

        if (lastSkippedBlock != -1L) {
            val skipped = fromHeight..lastSkippedBlock
            if (skipped.count() > 1) {
                subs.add("skipped ${skipped.count()} ($skipped)")
            } else {
                subs.add("skipped ${skipped.count()} (${skipped.first})")
            }
        }

        if (firstImportedBlock != -1L) {
            val imported = firstImportedBlock..toHeight
            if (imported.count() > 1) {
                subs.add("imported ${imported.count()} ($imported)")
            } else {
                subs.add("imported ${imported.count()} (${imported.first})")
            }
        }

        return subs.joinToString(", ")
    }
}
