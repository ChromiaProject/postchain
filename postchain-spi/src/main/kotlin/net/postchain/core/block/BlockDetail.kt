package net.postchain.core.block

import net.postchain.core.TxDetail
import net.postchain.core.hexStringLength
import net.postchain.gtv.mapper.Name

/**
 * BlockDetail returns a more in deep block overview
 * ATM it is mainly used to reply to explorer's queries
 */
data class BlockDetail(
        @Name("rid") val rid: ByteArray,
        @Name("prevBlockRID") val prevBlockRID: ByteArray,
        @Name("header") val header: ByteArray,
        @Name("height") val height: Long,
        @Name("transactions") val transactions: List<TxDetail>,
        @Name("witness") val witness: ByteArray,
        @Name("timestamp") val timestamp: Long
)

fun BlockDetail.size(): Int {
    val ridSize = rid.hexStringLength()
    val prevBlockRIDSize = prevBlockRID.hexStringLength()
    val headerSize = header.hexStringLength()
    val heightSize = height.toString().length
    val transactionsSize = transactions.sumOf { it.hexStringLength() }
    val witnessSize = witness.hexStringLength()
    val timestampSize = timestamp.toString().length

    return ridSize + prevBlockRIDSize + headerSize + heightSize + transactionsSize + witnessSize + timestampSize
}

fun ByteArray?.hexStringLength(): Int {
    return this?.size?.times(2) ?: 0
}

data class BlockDetailsTruncated(
        val blockDetails: List<BlockDetail>,
        val remainingTruncatedCount: Long
)
