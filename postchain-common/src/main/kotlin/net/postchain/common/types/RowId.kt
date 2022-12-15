package net.postchain.common.types

/**
 * Primary key of a database record.
 */
data class RowId(val id: Long) {
    init {
        require(id >= 0) { "Negative RowId is not allowed" }
    }
}
