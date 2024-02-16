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
import net.postchain.gtv.GtvFactory
import net.postchain.traces.OpenTelemetryFactory
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
        OpenTelemetryFactory.tracer = tracer
        val span: Span = tracer.spanBuilder("postTransaction").startSpan()
        span.makeCurrent()
        span.setAttribute("traceId", span.spanContext.traceId)
        span.setAttribute("spanId", span.spanContext.spanId)
        span.setAttribute("currentSpanId", Span.current().spanContext.spanId)
        span.setAttribute("currentTraceId", Span.current().spanContext.traceId)

        val sample = Timer.start(Metrics.globalRegistry)

        val validateTransactionSpan = tracer.spanBuilder("validateTransaction").startSpan()
        validateTransactionSpan.setAttribute("traceId", validateTransactionSpan.spanContext.traceId)
        validateTransactionSpan.setAttribute("spanId", validateTransactionSpan.spanContext.spanId)
        validateTransactionSpan.setAttribute("currentSpanId", Span.current().spanContext.spanId)
        validateTransactionSpan.setAttribute("currentTraceId", Span.current().spanContext.traceId)
        transactionFactory.validateTransaction(tx)
        validateTransactionSpan.end()

        val decodeTransactionSpan = tracer.spanBuilder("decodeTransaction").startSpan()
        decodeTransactionSpan.setAttribute("traceId", decodeTransactionSpan.spanContext.traceId)
        decodeTransactionSpan.setAttribute("spanId", decodeTransactionSpan.spanContext.spanId)
        decodeTransactionSpan.setAttribute("currentSpanId", Span.current().spanContext.spanId)
        decodeTransactionSpan.setAttribute("currentTraceId", Span.current().spanContext.traceId)
        val decodedTransaction = transactionFactory.decodeTransaction(tx)
        decodeTransactionSpan.end()

        span.setAttribute("txID", decodedTransaction.getRID().toHex())
        span.setAttribute("txRawData", decodedTransaction.getRawData().decodeToString())

        val checkCorrectnessSpan = tracer.spanBuilder("checkCorrectness").startSpan()
        checkCorrectnessSpan.setAttribute("traceId", checkCorrectnessSpan.spanContext.traceId)
        checkCorrectnessSpan.setAttribute("spanId", checkCorrectnessSpan.spanContext.spanId)
        checkCorrectnessSpan.setAttribute("currentSpanId", Span.current().spanContext.spanId)
        checkCorrectnessSpan.setAttribute("currentTraceId", Span.current().spanContext.traceId)
        decodedTransaction.checkCorrectness()
        checkCorrectnessSpan.end()

        if (blockQueries.isTransactionConfirmed(decodedTransaction.getRID()).get()) {
            sample.stop(metrics.duplicateTransactions)
            throw DuplicateTnxException("Transaction already in database")
        }

        val txEnqueueSpan = tracer.spanBuilder("txQueue.enqueue").startSpan()
        txEnqueueSpan.setAttribute("traceId", txEnqueueSpan.spanContext.traceId)
        txEnqueueSpan.setAttribute("spanId", txEnqueueSpan.spanContext.spanId)
        txEnqueueSpan.setAttribute("currentSpanId", Span.current().spanContext.spanId)
        txEnqueueSpan.setAttribute("currentTraceId", Span.current().spanContext.traceId)
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
