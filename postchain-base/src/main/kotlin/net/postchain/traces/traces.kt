package net.postchain.traces

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

object OpenTelemetryFactory {
    private val openTelemetry: OpenTelemetry by lazy { initializeOpenTelemetry() }
    var tracer: Tracer? = null

    private fun initializeOpenTelemetry(): OpenTelemetry {
        return AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
    }

    fun getOpenTelemetrySdk(): OpenTelemetry {
        return openTelemetry
    }
}
