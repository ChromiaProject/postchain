package net.postchain.core.block

import net.postchain.core.TxDetail
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

/**
 * BlockDetail returns a more in deep block overview
 * ATM it is mainly used to reply to explorer's queries
 */
open class BlockDetail(
    val rid: ByteArray,
    val prevBlockRID: ByteArray,
    val header: ByteArray,
    val height: Long,
    val transactions: List<TxDetail>,
    val witness: ByteArray,
    val timestamp: Long
    ) {

    fun toGtv(): Gtv {
        return gtv(mapOf(
                "rid" to gtv(rid),
                "prevBlockRID" to gtv(prevBlockRID),
                "header" to gtv(header),
                "height" to gtv(height),
                "transactions" to gtv(transactions.map { it.toGtv() }),
                "witness" to gtv(witness),
                "timestamp" to gtv(timestamp)
        ))
    }
}

