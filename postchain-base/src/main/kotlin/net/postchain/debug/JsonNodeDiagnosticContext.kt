package net.postchain.debug

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid

class JsonNodeDiagnosticContext(
        private val properties: DiagnosticData,
        private val blockchainDiagnosticData: MutableMap<BlockchainRid, DiagnosticData>
) : NodeDiagnosticContext,
        MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(version: String, pubKey: String, infrastructure: String) : this(
            DiagnosticProperty.VERSION withValue version,
            DiagnosticProperty.PUB_KEY withValue pubKey,
            DiagnosticProperty.BLOCKCHAIN_INFRASTRUCTURE withValue infrastructure
    )

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>) : this(DiagnosticData(*values), mutableMapOf())
    private val json = JsonFactory.makePrettyJson()

    init {
        properties[DiagnosticProperty.BLOCKCHAIN] = LazyDiagnosticValueCollection { blockchainDiagnosticData.values }
    }

    override fun blockchainErrorQueue(blockchainRid: BlockchainRid) = blockchainData(blockchainRid)[DiagnosticProperty.ERROR] as DiagnosticQueue<String>
    override fun hasBlockchainErrors(blockchainRid: BlockchainRid) = blockchainDiagnosticData.containsKey(blockchainRid) && blockchainErrorQueue(blockchainRid).isNotEmpty()

    override fun blockchainData(blockchainRid: BlockchainRid) = blockchainDiagnosticData.getOrPut(blockchainRid) {
        DiagnosticData(
                DiagnosticProperty.BLOCKCHAIN_RID withValue blockchainRid.toHex(),
                DiagnosticProperty.ERROR to DiagnosticQueue<String>(5)
        )
    }

    override fun removeBlockchainData(blockchainRid: BlockchainRid?) = blockchainDiagnosticData.remove(blockchainRid)

    override fun clearBlockchainData() = blockchainDiagnosticData.clear()

    override fun remove(k: DiagnosticProperty) = properties.remove(k)
    override fun isEmpty() = properties.isEmpty()
    override val size: Int
        get() = properties.size


    override fun format(): String = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }.let(json::toJson)
}
