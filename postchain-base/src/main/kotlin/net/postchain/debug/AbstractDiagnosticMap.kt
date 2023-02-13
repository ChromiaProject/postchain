package net.postchain.debug

abstract class AbstractDiagnosticMap(
        protected val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = mutableMapOf()
): AbstractMutableSet<DiagnosticValue>() {
    override fun add(element: DiagnosticValue) = properties.put(element.property, element) != null

    override fun iterator() = properties.values.iterator()

    override val size: Int get() = properties.size
}