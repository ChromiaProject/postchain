// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.DuplicateTnxException
import net.postchain.api.rest.controller.InvalidTnxException
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.Storage
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries
import net.postchain.debug.DiagnosticData
import net.postchain.traces.OpenTelemetryFactory

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

        val tracer: Tracer = OpenTelemetryFactory.getOpenTelemetrySdk().getTracer(PostchainEBFTModel::class.simpleName.toString())
        OpenTelemetryFactory.tracer = tracer
        val span: Span = tracer.spanBuilder("postTransaction").startSpan()

        span.makeCurrent()

        val sample = Timer.start(Metrics.globalRegistry)

        val validateTransactionSpan = tracer.spanBuilder("validateTransaction").setParent(Context.current().with(span)).startSpan()
        validateTransactionSpan.makeCurrent()
        transactionFactory.validateTransaction(tx)
        validateTransactionSpan.end()

        val decodeTransactionSpan = tracer.spanBuilder("decodeTransaction").setParent(Context.current().with(span)).startSpan()
        decodeTransactionSpan.makeCurrent()
        val decodedTransaction = transactionFactory.decodeTransaction(tx)
        decodeTransactionSpan.end()

        val checkCorrectnessSpan = tracer.spanBuilder("checkCorrectness").setParent(Context.current().with(span)).startSpan()
        checkCorrectnessSpan.makeCurrent()
        decodedTransaction.checkCorrectness()
        checkCorrectnessSpan.end()

        if (blockQueries.isTransactionConfirmed(decodedTransaction.getRID()).get()) {
            sample.stop(metrics.duplicateTransactions)
            throw DuplicateTnxException("Transaction already in database")
        }

        val txEnqueueSpan = tracer.spanBuilder("txQueue.enqueue").setParent(Context.current().with(span)).startSpan()
        txEnqueueSpan.makeCurrent()
        when (txQueue.enqueue(decodedTransaction)) {
            EnqueueTransactionResult.FULL -> {
                sample.stop(metrics.fullTransactions)
                txEnqueueSpan.addEvent("Transaction queue is full")
                throw UnavailableException("Transaction queue is full")
            }

            EnqueueTransactionResult.INVALID -> {
                sample.stop(metrics.invalidTransactions)
                txEnqueueSpan.addEvent("Transaction is invalid")
                throw InvalidTnxException("Transaction is invalid")
            }

            EnqueueTransactionResult.DUPLICATE -> {
                sample.stop(metrics.duplicateTransactions)
                txEnqueueSpan.addEvent("Transaction already in queue")
                throw DuplicateTnxException("Transaction already in queue")
            }

            EnqueueTransactionResult.OK -> {
                sample.stop(metrics.okTransactions)
                txEnqueueSpan.addEvent("Transaction ok")
            }
        }
        txEnqueueSpan.end()
        span.end()
    }
}
