// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.NodeDiagnosticContext

interface DebugInfoQuery {

    /**
     * Returns string representation of [NodeDiagnosticContext] object converted to Json
     */
    fun queryDebugInfo(query: String?): JsonElement
}

class DisabledDebugInfoQuery : DebugInfoQuery {
    override fun queryDebugInfo(query: String?): JsonElement = JsonObject().apply {
        addProperty("error", "Debug endpoint is not enabled. Use --debug cli option to enable it.")
    }
}

class DefaultDebugInfoQuery(val nodeDiagnosticContext: NodeDiagnosticContext) : DebugInfoQuery {

    override fun queryDebugInfo(query: String?): JsonElement = when (query) {
        null -> default()
        DiagnosticProperty.BLOCK_STATS.prettyName -> blockStats()
        else -> unknownQuery(query)
    }

    private fun default(): JsonElement {
        val json = nodeDiagnosticContext.format()
        json.asJsonObject.get(DiagnosticProperty.BLOCKCHAIN.prettyName)?.asJsonArray?.forEach {
            it.asJsonObject.remove(DiagnosticProperty.BLOCK_STATS.prettyName)
        }
        return json
    }

    private fun blockStats(): JsonElement = JsonArray().apply {
        nodeDiagnosticContext.format().asJsonObject.get(DiagnosticProperty.BLOCKCHAIN.prettyName)?.asJsonArray?.forEach {
            add(JsonObject().apply {
                add(DiagnosticProperty.BLOCKCHAIN_RID.prettyName, it.asJsonObject.get(DiagnosticProperty.BLOCKCHAIN_RID.prettyName))
                add(DiagnosticProperty.BLOCK_STATS.prettyName, it.asJsonObject.get(DiagnosticProperty.BLOCK_STATS.prettyName))
            })
        }
    }

    private fun unknownQuery(query: String): JsonElement = JsonObject().apply {
        addProperty("Error", "Unknown query: $query")
    }
}
