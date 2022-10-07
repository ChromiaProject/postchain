// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.ConfirmationProof
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.Gtv

interface ChainModel {
    val chainIID: Long
}

interface ExternalModel : ChainModel {
    val path: String
}

interface Model : ChainModel {
    fun postTransaction(tx: ApiTx)
    fun getTransaction(txRID: TxRID): ApiTx?
    fun getTransactionInfo(txRID: TxRID): TransactionInfoExt?
    fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt>
    fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): BlockDetail?
    fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail?
    fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail>
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

