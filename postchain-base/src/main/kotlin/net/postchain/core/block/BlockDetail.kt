package net.postchain.core.block

import net.postchain.core.TxDetail

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

}

