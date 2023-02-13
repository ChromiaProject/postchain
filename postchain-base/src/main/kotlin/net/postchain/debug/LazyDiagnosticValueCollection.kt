package net.postchain.debug

class LazyDiagnosticValueCollection(
        property: DiagnosticProperty,
        private val lazyCollection: () -> MutableCollection<DiagnosticValue>
) : AbstractDiagnosticValue(property) {
    override val value
        get() = lazyCollection().map { it.value }
}
