package net.postchain.debug

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid
import net.postchain.core.InfrastructureFactory
import java.util.Collections

class JsonNodeDiagnosticContext private constructor(
        private val properties: DiagnosticData,
        private val blockchainDiagnosticData: MutableMap<BlockchainRid, DiagnosticData>
) : NodeDiagnosticContext,
        MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(version: String, pubKey: String, infrastructure: InfrastructureFactory) : this(
            DiagnosticProperty.VERSION withValue version,
            DiagnosticProperty.PUB_KEY withValue pubKey,
            DiagnosticProperty.INFRASTRUCTURE_NAME withValue infrastructure::class.java.name,
            DiagnosticProperty.INFRASTRUCTURE_VERSION withValue (infrastructure::class.java.`package`.implementationVersion
                    ?: "(unknown)"),
    )

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>) : this(DiagnosticData(*values), Collections.synchronizedMap(mutableMapOf()))

    private val json = JsonFactory.makeJson()

    init {
        properties[DiagnosticProperty.BLOCKCHAIN] = LazyDiagnosticValueCollection { blockchainDiagnosticData.values }
    }

    override fun blockchainErrorQueue(blockchainRid: BlockchainRid) = blockchainData(blockchainRid)[DiagnosticProperty.ERROR] as DiagnosticQueue
    override fun blockchainBlockStats(blockchainRid: BlockchainRid) = blockchainData(blockchainRid)[DiagnosticProperty.BLOCK_STATS] as DiagnosticQueue
    override fun hasBlockchainErrors(blockchainRid: BlockchainRid) = blockchainDiagnosticData.containsKey(blockchainRid) && blockchainErrorQueue(blockchainRid).isNotEmpty()

    override fun blockchainData(blockchainRid: BlockchainRid) = blockchainDiagnosticData.getOrPut(blockchainRid) {
        DiagnosticData(
                DiagnosticProperty.BLOCKCHAIN_RID withValue blockchainRid.toHex(),
                DiagnosticProperty.ERROR to DiagnosticQueue(5),
                DiagnosticProperty.BLOCK_STATS to DiagnosticQueue(100)
        )
    }

    override fun blockchainData(): Map<BlockchainRid, DiagnosticData> = blockchainDiagnosticData

    override fun removeBlockchainData(blockchainRid: BlockchainRid?) = blockchainDiagnosticData.remove(blockchainRid)

    override fun clearBlockchainData() = blockchainDiagnosticData.clear()

    override fun remove(key: DiagnosticProperty) = properties.remove(key)
    override fun isEmpty() = properties.isEmpty()
    override val size: Int
        get() = properties.size

    override fun format(): JsonElement = JsonObject().apply {
        properties.forEach { (p, v) -> add(p.prettyName, json.toJsonTree(v.value)) }
    }
}
