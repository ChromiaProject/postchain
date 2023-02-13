package net.postchain.debug

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory

class JsonNodeDiagnosticContext(
        private val properties: DiagnosticData
) : NodeDiagnosticContext,
        MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>): this(DiagnosticData(*values))

    private val json = JsonFactory.makePrettyJson()

    override fun remove(k: DiagnosticProperty) = properties.remove(k)
    override fun isEmpty() = properties.isEmpty()
    override val size: Int
        get() = properties.size


    override fun format(): String = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }.let(json::toJson)
}
