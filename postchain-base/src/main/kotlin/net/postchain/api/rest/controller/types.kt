package net.postchain.api.rest.controller

import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.ConfirmationProof
import net.postchain.core.BlockDetail
import net.postchain.core.TxDetail
import net.postchain.gtv.Gtv

interface Model {
    val chainIID: Long
    fun postTransaction(tx: ApiTx)
    fun getTransaction(txRID: TxRID): ApiTx?
    fun getBlocks(blockHeight: Long, asc: Boolean, limit: Int, hashesOnly: Boolean): List<BlockDetail>
    fun getConfirmationProof(txRID: TxRID): ConfirmationProof?
    fun getStatus(txRID: TxRID): ApiStatus
    fun query(query: Query): QueryResult
    fun query(query: Gtv): Gtv
    fun nodeQuery(subQuery: String): String
    fun debugQuery(subQuery: String?): String
}

data class Query(val json: String)
data class QueryResult(val json: String)
data class BlockHeight(val blockHeight: Long)
data class ErrorBody(val error: String = "")

class NotSupported(message: String) : Exception(message)
class NotFoundError(message: String) : Exception(message)
class BadFormatError(message: String) : Exception(message)
class OverloadedException(message: String) : Exception(message)
class InvalidTnxException(message: String) : Exception(message)
class DuplicateTnxException(message: String) : Exception(message)
