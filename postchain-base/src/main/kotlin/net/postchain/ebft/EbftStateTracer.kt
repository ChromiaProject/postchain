// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope

class EbftStateTracer(val tracer: Tracer) {
    private var ebftSpan: Span
    private var ebftScope: Scope
    private var currentStateSpan: Span
    private var currentScope: Scope

    init {
        ebftSpan = tracer.spanBuilder(this.javaClass.simpleName).startSpan()
        ebftScope = ebftSpan.makeCurrent()
        currentStateSpan = tracer.spanBuilder(NodeBlockState.WaitBlock.name).startSpan()
        currentScope = currentStateSpan.makeCurrent()
    }

    fun startEbftSpan() {
        ebftSpan = tracer.spanBuilder(this.javaClass.simpleName).startSpan()
        ebftScope = ebftSpan.makeCurrent()
    }
    fun endEbftSpan() {
        ebftSpan.end()
        ebftScope.close()
    }

    fun startEbftStateSpan(state: NodeBlockState) {
        currentStateSpan = tracer.spanBuilder(state.name).startSpan()
        currentScope = currentStateSpan.makeCurrent()
    }

    fun endEbftStateSpan() {
        currentStateSpan.end()
        currentScope.close()
    }

    fun toWaitBlock(): Span {
        endEbftStateSpan()
        endEbftSpan()
        startEbftSpan()
        startEbftStateSpan(NodeBlockState.WaitBlock)
        return currentStateSpan
    }

    fun toHaveBlock(): Span {
        endEbftStateSpan()
        startEbftStateSpan(NodeBlockState.HaveBlock)
        return currentStateSpan
    }

    fun toPrepared(): Span {
        endEbftStateSpan()
        startEbftStateSpan(NodeBlockState.Prepared)
        return currentStateSpan
    }

    fun getActiveSpan() = currentStateSpan

    fun toIntent(intent: BlockIntent, message: String = "") {
        getActiveSpan().addEvent("set intent to ${intent.javaClass.simpleName}: $message")
    }

    fun startSpanUnderCurrentNodeBlockState(name: String): Span {
        return tracer.spanBuilder(name).setParent(Context.current().with(getActiveSpan())).startSpan()
    }
}
