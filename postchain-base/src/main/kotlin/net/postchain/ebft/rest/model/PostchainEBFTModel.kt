// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.DuplicateTnxException
import net.postchain.api.rest.controller.InvalidTnxException
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.Storage
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries
import net.postchain.debug.DiagnosticData

class PostchainEBFTModel(
        blockchainConfiguration: BlockchainConfiguration,
        private val txQueue: TransactionQueue,
        blockQueries: BlockQueries,
        blockchainRid: BlockchainRid,
        storage: Storage,
        postchainContext: PostchainContext,
        diagnosticData: DiagnosticData,
        queryCacheTtlSeconds: Long
) : PostchainModel(blockchainConfiguration, blockQueries, blockchainRid, storage, postchainContext, diagnosticData, queryCacheTtlSeconds) {
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
            throw DuplicateTnxException("Transaction already in database")
        }

        when (txQueue.enqueue(decodedTransaction)) {
            EnqueueTransactionResult.FULL -> {
                sample.stop(metrics.fullTransactions)
                throw UnavailableException("Transaction queue is full")
            }

            EnqueueTransactionResult.INVALID -> {
                sample.stop(metrics.invalidTransactions)
                throw InvalidTnxException("Transaction is invalid")
            }

            EnqueueTransactionResult.DUPLICATE -> {
                sample.stop(metrics.duplicateTransactions)
                throw DuplicateTnxException("Transaction already in queue")
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
            val exception = txQueue.getRejectionReason(txRID.bytes.wrap())
            ApiStatus(status, exception?.message)
        } else {
            ApiStatus(status)
        }
    }
}
