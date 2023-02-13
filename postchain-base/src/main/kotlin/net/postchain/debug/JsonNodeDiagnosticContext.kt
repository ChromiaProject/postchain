package net.postchain.debug

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory

class JsonNodeDiagnosticContext(
        properties: MutableMap<DiagnosticProperty, DiagnosticValue> = mutableMapOf()
) : NodeDiagnosticContext, AbstractDiagnosticMap(properties) {

    private val json = JsonFactory.makePrettyJson()
    override fun get(k: DiagnosticProperty) = properties[k]
    override fun remove(k: DiagnosticProperty) = properties.remove(k)

    override fun contains(k: DiagnosticProperty) = properties.containsKey(k)

    override fun format(): String = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }.let(json::toJson)
}