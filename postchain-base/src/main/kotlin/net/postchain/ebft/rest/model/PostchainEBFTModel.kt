// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.DebugInfoQuery
import net.postchain.api.rest.controller.DuplicateTnxException
import net.postchain.api.rest.controller.InvalidTnxException
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.concurrent.util.get
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries
import net.postchain.debug.DiagnosticData

class PostchainEBFTModel(
        chainIID: Long,
        txQueue: TransactionQueue,
        private val transactionFactory: TransactionFactory,
        blockQueries: BlockQueries,
        debugInfoQuery: DebugInfoQuery,
        blockchainRid: BlockchainRid,
        storage: Storage,
        postchainContext: PostchainContext,
        diagnosticData: DiagnosticData
) : PostchainModel(chainIID, txQueue, blockQueries, debugInfoQuery, blockchainRid, storage, postchainContext, diagnosticData) {

    override fun postTransaction(tx: ByteArray) {
        val sample = Timer.start(Metrics.globalRegistry)

        transactionFactory.validateTransaction(tx)
        val decodedTransaction = transactionFactory.decodeTransaction(tx)

        decodedTransaction.checkCorrectness()

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
}
