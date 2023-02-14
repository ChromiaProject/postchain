package net.postchain.debug

class LazyDiagnosticValueCollection(
        private val lazyCollection: () -> Collection<DiagnosticValue>
) : DiagnosticValue {
    override val value
        get() = lazyCollection().map { it.value }
}
