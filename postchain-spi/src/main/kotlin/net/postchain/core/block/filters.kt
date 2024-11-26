package net.postchain.core.block

interface Filter {
    val before: Long
    val after: Long
}

data class BlockQueryTimeFilter(
        val beforeTime: Long = Long.MAX_VALUE,
        val afterTime: Long = -1,
): Filter {
    init {
        require(afterTime < beforeTime) { "after time must not be greater than before time" }
    }

    override val before: Long = beforeTime
    override val after: Long = afterTime
}

data class BlockQueryHeightFilter(
        val beforeHeight: Long = Long.MAX_VALUE,
        val afterHeight: Long = -1,
): Filter {
    init {
        require(afterHeight < beforeHeight) { "after height must not be greater than before height" }
    }

    override val before: Long = beforeHeight
    override val after: Long = afterHeight
}
