package net.postchain.debug

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory

class JsonNodeDiagnosticContext(
        private val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = mutableMapOf()
) : NodeDiagnosticContext,
        Map<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(vararg values: DiagnosticValue): this(values.associateBy { it.property }.toMutableMap())

    private val json = JsonFactory.makePrettyJson()

    override fun add(v: DiagnosticValue) = properties.put(v.property, v) != null

    override fun remove(k: DiagnosticProperty) = properties.remove(k)
    override fun isEmpty() = properties.isEmpty()
    override val size: Int
        get() = properties.size


    override fun format(): String = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }.let(json::toJson)
}
