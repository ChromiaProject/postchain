package net.postchain.trace

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope

class TransactionQueueTracer {

    private val txInQueueSpanMap = mutableMapOf<String, Pair<Span, Scope>>()
    private val tracer = GlobalOpenTelemetry.getTracer(this.javaClass.simpleName)

    fun startTxEnqueueSpan(txRid: String): Span {
        val span = tracer.spanBuilder("txQueue.enqueue").setParent(Context.current()).startSpan()
        span.setAttribute("txRID", txRid)
        return span
    }

    fun startTxInQueueSpan(txRid: String, txEnqueueSpan: Span): Span {
        val span = tracer.spanBuilder("tx in queue").addLink(txEnqueueSpan.spanContext).startSpan()
        span.setAttribute("txRID", txRid)
        span.addEvent("tx $txRid in queue")

        txInQueueSpanMap.putIfAbsent(txRid, span to span.makeCurrent())
        return span
    }

    fun getTxInQueueSpan(txRid: String): Span? {
        return txInQueueSpanMap.get(txRid)?.first
    }

    fun endTxInQueueSpan(txRid: String) {
        val (span, scope) = txInQueueSpanMap.get(txRid) ?: return
        txInQueueSpanMap.remove(txRid)
        span.addEvent("tx $txRid out queue")
        span.end()
        scope.close()
    }
}