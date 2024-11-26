// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.NodeDiagnosticContext

interface DebugInfoQuery {

    /**
     * Returns string representation of [NodeDiagnosticContext] object converted to Json
     */
    fun all(): JsonElement

    /**
     * Returns string representation of [NodeDiagnosticContext] object converted to Json
     * Without including block statistics
     */
    fun default(): JsonElement

    /**
     * Returns string representation of block statistics in [NodeDiagnosticContext] object converted to Json
     */
    fun blockStats(): JsonElement
}

class DefaultDebugInfoQuery(val nodeDiagnosticContext: NodeDiagnosticContext) : DebugInfoQuery {

    override fun all(): JsonElement = nodeDiagnosticContext.format()

    override fun default(): JsonElement {
        val json = nodeDiagnosticContext.format()
        json.asJsonObject.get(DiagnosticProperty.BLOCKCHAIN.prettyName)?.asJsonArray?.forEach {
            it.asJsonObject.remove(DiagnosticProperty.BLOCK_STATS.prettyName)
        }
        return json
    }

    override fun blockStats(): JsonElement {
        val json = nodeDiagnosticContext.format()
        val blockchains = json.asJsonObject.get(DiagnosticProperty.BLOCKCHAIN.prettyName)?.asJsonArray ?: JsonArray()
        blockchains.forEach {
            val blockchain = it.asJsonObject
            blockchain.keySet()
                    .filter { key -> key != DiagnosticProperty.BLOCKCHAIN_RID.prettyName && key != DiagnosticProperty.BLOCK_STATS.prettyName }
                    .forEach { key -> blockchain.remove(key) }
        }
        return blockchains
    }
}
