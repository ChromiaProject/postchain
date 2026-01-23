package net.postchain.core.block

import net.postchain.core.TxDetail
import net.postchain.core.hexStringLength
import net.postchain.gtv.mapper.Name

/**
 * BlockDetail returns a more in deep block overview
 * ATM it is mainly used to reply to explorer's queries
 */
data class BlockDetail(
        @param:Name("rid") val rid: ByteArray,
        @param:Name("prevBlockRID") val prevBlockRID: ByteArray,
        @param:Name("header") val header: ByteArray,
        @param:Name("height") val height: Long,
        @param:Name("transactions") val transactions: List<TxDetail>,
        @param:Name("witness") val witness: ByteArray,
        @param:Name("timestamp") val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlockDetail

        if (!rid.contentEquals(other.rid)) return false
        if (!prevBlockRID.contentEquals(other.prevBlockRID)) return false
        if (!header.contentEquals(other.header)) return false
        if (height != other.height) return false
        if (transactions != other.transactions) return false
        if (!witness.contentEquals(other.witness)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rid.contentHashCode()
        result = 31 * result + prevBlockRID.contentHashCode()
        result = 31 * result + header.contentHashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + transactions.hashCode()
        result = 31 * result + witness.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

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
        val truncated: Boolean
)
