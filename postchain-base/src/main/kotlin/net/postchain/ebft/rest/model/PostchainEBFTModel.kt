// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.DuplicateException
import net.postchain.api.rest.controller.InvalidTnxException
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.api.rest.model.ApiRejectedTransaction
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.AsyncQueryQueue
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.Storage
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries
import net.postchain.debug.DiagnosticData
import net.postchain.debug.NodeDiagnosticContext
import java.time.Instant

class PostchainEBFTModel(
        blockchainConfiguration: BlockchainConfiguration,
        private val txQueue: TransactionQueue,
        blockQueries: BlockQueries,
        blockchainRid: BlockchainRid,
        storage: Storage,
        postchainContext: PostchainContext,
        nodeDiagnosticContext: NodeDiagnosticContext,
        diagnosticData: DiagnosticData,
        queryCacheTtlSeconds: Long,
        asyncQueryQueue: AsyncQueryQueue,
) : PostchainModel(blockchainConfiguration, blockQueries, blockchainRid, storage, postchainContext, nodeDiagnosticContext, diagnosticData, queryCacheTtlSeconds, asyncQueryQueue) {
    private val transactionFactory = blockchainConfiguration.getTransactionFactory()

    override fun postTransaction(tx: ByteArray) {
        val sample = Timer.start(Metrics.globalRegistry)

        val decodedTransaction = transactionFactory.decodeAndValidateTransaction(tx)

        decodedTransaction.checkCorrectness()

        if (decodedTransaction.isSpecial()) {
            sample.stop(metrics.invalidTransactions)
            throw InvalidTnxException("Cannot post special transaction")
        }

        if (blockQueries.isTransactionConfirmed(decodedTransaction.getRID()).get()) {
            sample.stop(metrics.duplicateTransactions)
            throw DuplicateException("Transaction already in database")
        }

        when (txQueue.enqueue(decodedTransaction)) {
            EnqueueTransactionResult.FULL -> {
                sample.stop(metrics.fullTransactions)
                throw UnavailableException("Transaction queue is full")
            }

            EnqueueTransactionResult.INVALID -> {
                sample.stop(metrics.invalidTransactions)
                val rejectionReason = txQueue.getRejectionReason(decodedTransaction.getRID().wrap())?.first?.message
                        ?: "Transaction is invalid"
                throw InvalidTnxException(rejectionReason)
            }

            EnqueueTransactionResult.DUPLICATE -> {
                sample.stop(metrics.duplicateTransactions)
                throw DuplicateException("Transaction already in queue")
            }

            EnqueueTransactionResult.OK -> {
                sample.stop(metrics.okTransactions)
            }
        }
    }

    override fun getStatus(txRID: TxRid): ApiStatus {
        var status = txQueue.getTransactionStatus(txRID.bytes)

        if (status == UNKNOWN) {
            status = if (blockQueries.isTransactionConfirmed(txRID.bytes).get())
                CONFIRMED else UNKNOWN
        }

        return if (status == REJECTED) {
            val exceptionAndTimestamp = txQueue.getRejectionReason(txRID.bytes.wrap())
            ApiStatus(status, exceptionAndTimestamp?.first?.message, exceptionAndTimestamp?.second?.toEpochMilli())
        } else {
            ApiStatus(status)
        }
    }

    override fun getWaitingTransactions(): List<TxRid> = txQueue.waitingTransactions().map { TxRid(it.getRID()) }

    override fun getWaitingTransaction(txRID: TxRid): Pair<ByteArray, Instant>? = txQueue.waitingTransaction(txRID.bytes.wrap())

    override fun getRejectedTransactions(): List<ApiRejectedTransaction> =
            txQueue.rejectedTransactions().map {
                ApiRejectedTransaction(TxRid(it.txRID.data), it.reason.message ?: "", it.timestamp.toEpochMilli())
            }
}
