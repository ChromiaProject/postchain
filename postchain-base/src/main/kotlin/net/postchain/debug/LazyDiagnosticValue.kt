package net.postchain.debug

class LazyDiagnosticValue(property: DiagnosticProperty, val lazyValue: () -> Any?) : AbstractDiagnosticValue(property) {
    override val value
        get() = try {
            lazyValue()
        } catch (e: Exception) {
            "Unable to fetch value, ${e.message}"
        }
}
