package net.postchain.debug

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid

class JsonNodeDiagnosticContext(
        private val properties: DiagnosticData,
        override val blockchainDiagnosticData: MutableMap<BlockchainRid, DiagnosticData>
) : NodeDiagnosticContext,
        MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(version: String, pubKey: String, infrastructure: String) : this(
            DiagnosticProperty.VERSION withValue version,
            DiagnosticProperty.PUB_KEY withValue pubKey,
            DiagnosticProperty.BLOCKCHAIN_INFRASTRUCTURE withValue infrastructure
    )

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>) : this(DiagnosticData(*values), mutableMapOf())

    init {
        properties[DiagnosticProperty.BLOCKCHAIN] = LazyDiagnosticValueCollection { blockchainDiagnosticData.values }
    }

    private val json = JsonFactory.makePrettyJson()

    override fun remove(k: DiagnosticProperty) = properties.remove(k)
    override fun isEmpty() = properties.isEmpty()
    override val size: Int
        get() = properties.size


    override fun format(): String = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }.let(json::toJson)
}
