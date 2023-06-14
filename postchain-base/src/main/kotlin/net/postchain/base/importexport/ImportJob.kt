package net.postchain.base.importexport

data class ImportJob(
        val jobId: Int,
        val chainId: Long,
        val configurationsFile: String,
        val blocksFile: String,
        val state: ImportJobState
)

enum class ImportJobState {
    CONFIGURATIONS, BLOCKS
}
