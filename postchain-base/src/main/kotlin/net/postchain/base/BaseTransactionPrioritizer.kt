package net.postchain.base

import net.postchain.common.types.WrappedByteArray
import net.postchain.concurrent.util.get
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GtxBody
import java.math.BigDecimal
import java.time.Instant

const val PRIORITIZE_QUERY_NAME_V1 = "gtx_api.priority_check_v1"
const val PRIORITIZE_QUERY_NAME_V2 = "gtx_api.priority_check_v2"

class PrioritizeQueryV1Request(
        @param:Name("tx_body")
        val txBody: GtxBody,

        @param:Name("tx_size")
        val txSize: Long,

        @param:Name("tx_enter_timestamp")
        val txEnterTimestamp: Long,

        @param:Name("current_timestamp")
        val currentTimestamp: Long
)

class PrioritizeQueryV2Request(
        @param:Name("tx_body")
        val txBody: GtxBody,

        @param:Name("tx_size")
        val txSize: Long,

        @param:Name("tx_enter_timestamp")
        val txEnterTimestamp: Long,

        @param:Name("current_timestamp")
        val currentTimestamp: Long,

        @param:Name("compound_ops")
        val compoundOps: Set<String>
)

class TxPriorityStateV1(
        /** id of account which wants to push the tx forward */
        @param:Name("account_id")
        @param:Nullable
        override val accountId: WrappedByteArray?,

        /** number of points currently associated with the account */
        @param:Name("account_points")
        override val accountPoints: Long,

        /** number of points which this tx costs */
        @param:Name("tx_cost_points")
        override val txCostPoints: Long,

        /** priority, higher is better */
        @param:Name("priority")
        override val priority: BigDecimal
) : TransactionPriorityState

class BaseTransactionPrioritizerV1(private val blockQueries: BlockQueries) : TransactionPrioritizer {
    override fun prioritize(tx: GTXTransaction, txEnter: Instant, current: Instant): TransactionPriorityState {
        return GtvObjectMapper.fromGtv(blockQueries.query(PRIORITIZE_QUERY_NAME_V1,
                GtvObjectMapper.toGtvDictionary(PrioritizeQueryV1Request(
                        txBody = tx.gtxData.gtxBody,
                        txSize = tx.getRawData().size.toLong(),
                        txEnterTimestamp = txEnter.toEpochMilli(),
                        currentTimestamp = current.toEpochMilli()
                ))).get(), TxPriorityStateV1::class)
    }
}

class BaseTransactionPrioritizerV2(private val blockQueries: BlockQueries) : TransactionPrioritizer {
    override fun prioritize(tx: GTXTransaction, txEnter: Instant, current: Instant): TransactionPriorityState {

        val compoundOps = tx.ops
                .filter { it.isCompound() }
                .filterIsInstance<GTXOperation>()
                .map { it.data.opName }
                .toSet()

        return GtvObjectMapper.fromGtv(blockQueries.query(PRIORITIZE_QUERY_NAME_V2,
                GtvObjectMapper.toGtvDictionary(PrioritizeQueryV2Request(
                        txBody = tx.gtxData.gtxBody,
                        txSize = tx.getRawData().size.toLong(),
                        txEnterTimestamp = txEnter.toEpochMilli(),
                        currentTimestamp = current.toEpochMilli(),
                        compoundOps = compoundOps
                ))).get(), TxPriorityStateV1::class)
    }
}
