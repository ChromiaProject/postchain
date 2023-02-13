package net.postchain.debug

class DiagnosticValueCollection(
        property: DiagnosticProperty,
        private val set: MutableCollection<DiagnosticValue>
) : AbstractDiagnosticValue(property), MutableCollection<DiagnosticValue> by set {
    override val value
        get() = set.map { it.value }
}
