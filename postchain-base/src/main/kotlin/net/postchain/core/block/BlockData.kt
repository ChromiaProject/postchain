package net.postchain.core.block

open class BlockData(
    val header: BlockHeader,
    val transactions: List<ByteArray>
)