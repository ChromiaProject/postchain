package net.postchain.debug

class OrderedDiagnosticValueSet(override val property: DiagnosticProperty, private val set: MutableCollection<DiagnosticValue>): DiagnosticValue, AbstractMutableSet<DiagnosticValue>() {

    override fun add(element: DiagnosticValue) = set.add(element)

    override val size get() = set.size

    override fun iterator() = set.iterator()

    override val value
        get() = set.sortedBy { it.property.prettyName }.map { it.value }
}
