package net.postchain.base

import net.postchain.core.Transaction
import net.postchain.core.TransactionPrioritizer
import net.postchain.core.TransactionPriority
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GtxOp
import net.postchain.managed.query.QueryRunner

const val PRIORITIZE_QUERY_NAME = "prioritize_transaction"

data class PrioritizeQueryRequest(
        @Name("signers")
        val signers: List<ByteArray>,
        @Name("operations")
        val operations: List<GtxOp>
)

data class PrioritizeQueryResponse(
        @Name("priority")
        val priority: Long
)

class BaseTransactionPrioritizer(private val query: QueryRunner) : TransactionPrioritizer {
    override fun prioritize(tx: Transaction): TransactionPriority = if (tx is GTXTransaction) {
        prioritize(tx)
    } else {
        TransactionPriority(
                priority = 0
        )
    }

    private fun prioritize(tx: GTXTransaction): TransactionPriority {
        val result = GtvObjectMapper.fromGtv(query.query(PRIORITIZE_QUERY_NAME, GtvObjectMapper.toGtvDictionary(PrioritizeQueryRequest(
                signers = tx.gtxData.gtxBody.signers,
                operations = tx.gtxData.gtxBody.operations
        ))), PrioritizeQueryResponse::class)
        return TransactionPriority(
                priority = result.priority.toInt()
        )
    }
}
