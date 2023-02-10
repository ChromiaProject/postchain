package net.postchain.debug

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory

class JsonNodeDiagnosticContext(
        private val properties: MutableMap<DiagnosticProperty, DiagnosticValue>
) : NodeDiagnosticContext, AbstractMutableMap<DiagnosticProperty, DiagnosticValue>() {

    private val json = JsonFactory.makePrettyJson()

    constructor() : this(mutableMapOf())

    override fun add(element: DiagnosticValue) = properties.put(element.property, element) != null
    override fun addAll(elements: Collection<DiagnosticValue>) = elements.all { add(it) }

    override fun contains(element: DiagnosticValue) = properties.containsValue(element)
    override fun containsAll(elements: Collection<DiagnosticValue>) = properties.values.containsAll(elements)
    override fun iterator() = properties.values.sortedBy { it.property.prettyName }.toMutableSet().iterator()
    override fun remove(element: DiagnosticValue): Boolean = properties.remove(element.property) != null
    override fun removeAll(elements: Collection<DiagnosticValue>): Boolean = elements.all(::remove)

    override fun retainAll(elements: Collection<DiagnosticValue>): Boolean { TODO("Not yet implemented") }

    override val entries: MutableSet<MutableMap.MutableEntry<DiagnosticProperty, DiagnosticValue>>
        get() = properties.entries

    override val size: Int
        get() = properties.size

    override fun put(key: DiagnosticProperty, value: DiagnosticValue): DiagnosticValue? {
        TODO("Not yet implemented")
    }

    override fun format(): String = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }.let(json::toJson)
}