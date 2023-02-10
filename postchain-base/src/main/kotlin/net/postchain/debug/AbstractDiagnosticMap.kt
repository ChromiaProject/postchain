package net.postchain.debug

abstract class AbstractDiagnosticMap(
        protected val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = mutableMapOf()
): MutableCollection<DiagnosticValue>, AbstractMutableMap<DiagnosticProperty, DiagnosticValue>() {

    override fun put(key: DiagnosticProperty, value: DiagnosticValue) = properties.put(key, value)
    override fun add(element: DiagnosticValue) = properties.put(element.property, element) != null
    override fun addAll(elements: Collection<DiagnosticValue>) = elements.all { add(it) }

    override fun contains(element: DiagnosticValue) = properties.containsValue(element)
    override fun containsAll(elements: Collection<DiagnosticValue>) = properties.values.containsAll(elements)
    override fun iterator() = properties.values.iterator()
    override fun remove(element: DiagnosticValue): Boolean = properties.remove(element.property) != null
    override fun removeAll(elements: Collection<DiagnosticValue>): Boolean = elements.all(::remove)

    override fun retainAll(elements: Collection<DiagnosticValue>): Boolean { TODO("Not yet implemented") }

    override val entries: MutableSet<MutableMap.MutableEntry<DiagnosticProperty, DiagnosticValue>>
        get() = properties.entries

    override val size: Int get() = properties.size


}