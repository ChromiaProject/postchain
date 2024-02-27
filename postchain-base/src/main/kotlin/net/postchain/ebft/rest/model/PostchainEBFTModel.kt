// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.DuplicateTnxException
import net.postchain.api.rest.controller.InvalidTnxException
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.Storage
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries
import net.postchain.debug.DiagnosticData
import java.util.*

class PostchainEBFTModel(
        blockchainConfiguration: BlockchainConfiguration,
        txQueue: TransactionQueue,
        blockQueries: BlockQueries,
        blockchainRid: BlockchainRid,
        storage: Storage,
        postchainContext: PostchainContext,
        diagnosticData: DiagnosticData,
        queryCacheTtlSeconds: Long
) : PostchainModel(blockchainConfiguration, txQueue, blockQueries, blockchainRid, storage, postchainContext, diagnosticData, queryCacheTtlSeconds) {
    private val transactionFactory = blockchainConfiguration.getTransactionFactory()

    override fun postTransaction(tx: ByteArray) {

        val tracer: Tracer = GlobalOpenTelemetry.getTracer("postTransaction-${UUID.randomUUID()}")
        val span: Span = tracer.spanBuilder("PostchainEBFTModel.postTransaction()").startSpan()
        val scope = span.makeCurrent()

        val sample = Timer.start(Metrics.globalRegistry)

        val validateTransactionSpan = tracer.spanBuilder("validateTransaction").startSpan()
        transactionFactory.validateTransaction(tx)
        validateTransactionSpan.end()

        val decodeTransactionSpan = tracer.spanBuilder("decodeTransaction").startSpan()
        val decodedTransaction = transactionFactory.decodeTransaction(tx)
        decodeTransactionSpan.end()

        span.setAttribute("txRID", decodedTransaction.getRID().toHex())
        span.setAttribute("txRawData", decodedTransaction.getRawData().decodeToString())

        val checkCorrectnessSpan = tracer.spanBuilder("checkCorrectness").startSpan()
        checkCorrectnessSpan.setAttribute("txRID", decodedTransaction.getRID().toHex())
        decodedTransaction.checkCorrectness()
        checkCorrectnessSpan.end()

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
        span.end()
        scope.close()
    }
}
