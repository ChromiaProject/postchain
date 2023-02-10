package net.postchain.debug

class LazyDiagnosticValue(override val property: DiagnosticProperty, val lazyValue: () -> Any?): DiagnosticValue {
    override val value get() = try {
        lazyValue()
    } catch (e: Exception) {
        "Unable to fetch value, ${e.message}"
    }
}
