// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.JsonObject
import net.postchain.debug.NodeDiagnosticContext

interface DebugInfoQuery {

    /**
     * Returns string representation of [NodeDiagnosticContext] object converted to Json
     */
    fun queryDebugInfo(query: String?): String
}

class DisabledDebugInfoQuery: DebugInfoQuery {
    override fun queryDebugInfo(query: String?): String {
        return JsonObject().apply {
            addProperty("error", "Debug endpoint is not enabled. Use --debug cli option to enable it.")
        }.toString()
    }
}

class DefaultDebugInfoQuery(val nodeDiagnosticContext: NodeDiagnosticContext) : DebugInfoQuery {

    override fun queryDebugInfo(query: String?): String {
        return when (query) {
            null -> collectDebugInfo()
            else -> unknownQuery(query)
        }
    }

    private fun collectDebugInfo() = nodeDiagnosticContext.format()

    private fun unknownQuery(query: String): String {
        return JsonObject().apply {
            addProperty("Error", "Unknown query: $query")
        }.toString()
    }
}
