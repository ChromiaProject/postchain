package net.postchain.base

import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GtxBody
import net.postchain.managed.query.QueryRunner
import java.math.BigDecimal
import java.time.Instant

const val PRIORITIZE_QUERY_NAME = "gtx_api.priority_check_v1"

class PrioritizeQueryRequest(
        @Name("tx_body")
        val txBody: GtxBody,

        @Name("tx_size")
        val txSize: Long,

        @Name("tx_enter_timestamp")
        val txEnterTimestamp: Long,

        @Name("current_timestamp")
        val currentTimestamp: Long
)

class TxPriorityStateV1(
        /** id of account which wants to push the tx forward */
        @Name("account_id")
        override val accountId: WrappedByteArray?,

        /** number of points currently associated with the account */
        @Name("account_points")
        override val accountPoints: Long,

        /** number of points which this tx costs */
        @Name("tx_cost_points")
        override val txCostPoints: Long,

        /** priority, higher is better */
        @Name("priority")
        override val priority: BigDecimal
) : TransactionPriorityState

class BaseTransactionPrioritizer(private val query: QueryRunner) : TransactionPrioritizer {
    override fun prioritize(tx: GTXTransaction, txEnter: Instant, current: Instant): TransactionPriorityState {
        return GtvObjectMapper.fromGtv(query.query(PRIORITIZE_QUERY_NAME, GtvObjectMapper.toGtvDictionary(PrioritizeQueryRequest(
                txBody = tx.gtxData.gtxBody,
                txSize = tx.getRawData().size.toLong(),
                txEnterTimestamp = txEnter.toEpochMilli(),
                currentTimestamp = current.toEpochMilli()
        ))), TxPriorityStateV1::class)
    }
}
