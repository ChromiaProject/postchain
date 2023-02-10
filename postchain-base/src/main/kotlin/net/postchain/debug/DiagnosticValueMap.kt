package net.postchain.debug

class DiagnosticValueMap(override val property: DiagnosticProperty, vararg values: DiagnosticValue): DiagnosticValue, MutableSet<DiagnosticValue>, AbstractMap<DiagnosticProperty, DiagnosticValue>() {
    private val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = values.associateBy { it.property }.toMutableMap()

    //override fun put(key: DiagnosticProperty, value: DiagnosticValue) = properties.put(key, value)

    override val entries: MutableSet<MutableMap.MutableEntry<DiagnosticProperty, DiagnosticValue>>
        get() = properties.entries
    override val value: Any?
        get() = properties.map { it.key.prettyName to it.value.value }.toMap()

    override fun add(element: DiagnosticValue) = properties.put(element.property, element) != null
    override fun addAll(elements: Collection<DiagnosticValue>) = elements.all { add(it) }

    override fun contains(element: DiagnosticValue) = properties.containsValue(element)
    override fun containsAll(elements: Collection<DiagnosticValue>) = properties.values.containsAll(elements)
    override fun clear() = properties.clear()

    override fun iterator() = properties.values.sortedBy { it.property.prettyName }.toMutableSet().iterator()
    override fun remove(element: DiagnosticValue): Boolean = properties.remove(element.property) != null
    override fun removeAll(elements: Collection<DiagnosticValue>): Boolean = elements.all(::remove)
    override fun retainAll(elements: Collection<DiagnosticValue>): Boolean { TODO("Not yet implemented") }

}
