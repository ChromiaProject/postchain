// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.BaseBlockQueries
import net.postchain.base.ConfirmationProof
import net.postchain.common.BlockchainRid
import net.postchain.common.TimeLog
import net.postchain.common.data.byteArrayKeyOf
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus.*
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.Gtv

private const val SUBMITTED_METRIC_NAME = "submitted.transactions"
private const val SUBMITTED_METRIC_DESCRIPTION = "Transactions submitted/enqueued"

open class PostchainModel(
    final override val chainIID: Long,
    val txQueue: TransactionQueue,
    private val transactionFactory: TransactionFactory,
    val blockQueries: BaseBlockQueries,
    private val debugInfoQuery: DebugInfoQuery,
    blockchainRid: BlockchainRid
) : Model {

    companion object : KLogging()

    private val fullTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.FULL.name)
        .register(Metrics.globalRegistry)

    private val invalidTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.INVALID.name)
        .register(Metrics.globalRegistry)

    private val duplicateTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.DUPLICATE.name)
        .register(Metrics.globalRegistry)

    private val unknownTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.UNKNOWN.name)
        .register(Metrics.globalRegistry)

    private val okTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.OK.name)
        .register(Metrics.globalRegistry)

    override fun postTransaction(tx: ApiTx) {
        val sample = Timer.start(Metrics.globalRegistry)

        var nonce = TimeLog.startSumConc("PostchainModel.postTransaction().decodeTransaction")
        val decodedTransaction = transactionFactory.decodeTransaction(tx.bytes)
        TimeLog.end("PostchainModel.postTransaction().decodeTransaction", nonce)

        nonce = TimeLog.startSumConc("PostchainModel.postTransaction().isCorrect")
        if (!decodedTransaction.isCorrect()) {
            throw UserMistake("Transaction ${decodedTransaction.getRID().toHex()} is not correct")
        }
        TimeLog.end("PostchainModel.postTransaction().isCorrect", nonce)
        nonce = TimeLog.startSumConc("PostchainModel.postTransaction().enqueue")
        when (txQueue.enqueue(decodedTransaction)) {
            EnqueueTransactionResult.FULL -> {
                sample.stop(fullTransactions)
                throw OverloadedException("Transaction queue is full")
            }
            EnqueueTransactionResult.INVALID -> {
                sample.stop(invalidTransactions)
                throw InvalidTnxException("Transaction is invalid")
            }
            EnqueueTransactionResult.DUPLICATE -> {
                sample.stop(duplicateTransactions)
                throw DuplicateTnxException("Transaction already in queue")
            }
            EnqueueTransactionResult.UNKNOWN -> {
                sample.stop(unknownTransactions)
                throw UserMistake("Unknown error")
            }
            EnqueueTransactionResult.OK -> {
                sample.stop(okTransactions)
            }
        }
        TimeLog.end("PostchainModel.postTransaction().enqueue", nonce)
    }

    override fun getTransaction(txRID: TxRID): ApiTx? {
        return blockQueries.getTransaction(txRID.bytes).get()
                .takeIf { it != null }
                ?.let { ApiTx(it.getRawData().toHex()) }
    }

    override fun getTransactionInfo(txRID: TxRID): TransactionInfoExt? {
        return blockQueries.getTransactionInfo(txRID.bytes).get()
    }

    override fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt> {
        return blockQueries.getTransactionsInfo(beforeTime, limit).get()
    }

    override fun getBlocks(beforeTime: Long, limit: Int, partialTx: Boolean): List<BlockDetail> {
        return blockQueries.getBlocks(beforeTime, limit, partialTx).get()
    }

    override fun getBlock(blockRID: ByteArray, partialTx: Boolean): BlockDetail? {
        return blockQueries.getBlock(blockRID, partialTx).get()
    }

    override fun getConfirmationProof(txRID: TxRID): ConfirmationProof? {
        return blockQueries.getConfirmationProof(txRID.bytes).get()
    }

    override fun getStatus(txRID: TxRID): ApiStatus {
        var status = txQueue.getTransactionStatus(txRID.bytes)

        if (status == UNKNOWN) {
            status = if (blockQueries.isTransactionConfirmed(txRID.bytes).get())
                CONFIRMED else UNKNOWN
        }

        return if (status == REJECTED) {
            val exception = txQueue.getRejectionReason(txRID.bytes.byteArrayKeyOf())
            ApiStatus(status, exception?.message)
        } else {
            ApiStatus(status)
        }
    }

    override fun query(query: Query): QueryResult {
        return QueryResult(blockQueries.query(query.json).get())
    }

    override fun query(query: Gtv): Gtv {
        return blockQueries.query(query[0].asString(), query[1]).get()
    }

    override fun nodeQuery(subQuery: String): String = throw NotSupported("NotSupported: $subQuery")

    override fun debugQuery(subQuery: String?): String {
        return debugInfoQuery.queryDebugInfo(subQuery)
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(chainId=$chainIID)"
    }
}
