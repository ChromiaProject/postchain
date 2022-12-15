package net.postchain.core.block

import net.postchain.core.TxDetail
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
