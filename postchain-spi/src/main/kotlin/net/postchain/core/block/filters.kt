package net.postchain.core.block

data class BlockQueryTimeFilter(
        val beforeTime: Long = Long.MAX_VALUE,
        val afterTime: Long = -1,
) {
    init {
        require(afterTime < beforeTime) { "after time must not be greater than before time" }
    }
}

data class BlockQueryHeightFilter(
        val beforeHeight: Long = Long.MAX_VALUE,
        val afterHeight: Long = -1,
) {
    init {
        require(afterHeight < beforeHeight) { "after height must not be greater than before height" }
    }
}
