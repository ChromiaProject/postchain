package net.postchain.base

import net.postchain.common.types.WrappedByteArray
import net.postchain.gtx.GTXTransaction
import java.math.BigDecimal
import java.time.Instant

interface TransactionPriorityState {
    /** id of account which wants to push the tx forward */
    val accountId: WrappedByteArray

    /** number of points currently associated with the account */
    val accountPoints: Long

    /** number of points which this tx costs */
    val txCostPoints: Long

    /** priority, higher is better */
    val priority: BigDecimal
}

fun interface TransactionPrioritizer {
    fun prioritize(tx: GTXTransaction, txEnter: Instant, current: Instant): TransactionPriorityState
}
