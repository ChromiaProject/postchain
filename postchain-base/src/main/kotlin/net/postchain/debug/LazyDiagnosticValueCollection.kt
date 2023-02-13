package net.postchain.debug

class LazyDiagnosticValueCollection(
        private val lazyCollection: () -> MutableCollection<DiagnosticValue>
) : DiagnosticValue {
    override val value
        get() = lazyCollection().map { it.value }
}
