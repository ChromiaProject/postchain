// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory
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
            addProperty("Error", "Debug endpoint is not enabled. Use --debug cli option to enable it.")
        }.toString()
    }
}

class DefaultDebugInfoQuery(val nodeDiagnosticContext: NodeDiagnosticContext) : DebugInfoQuery {

    private val jsonBuilder = JsonFactory.makePrettyJson()

    override fun queryDebugInfo(query: String?): String {
        return when (query) {
            null -> collectDebugInfo()
            else -> unknownQuery(query)
        }
    }

    private fun collectDebugInfo(): String {
        return JsonObject()
                .apply {
                    nodeDiagnosticContext.getProperties().forEach { (property, value) ->
                        add(property, jsonBuilder.toJsonTree(value))
                    }
                }.let(jsonBuilder::toJson)
    }

    private fun unknownQuery(query: String): String {
        return JsonObject().apply {
            addProperty("Error", "Unknown query: $query")
        }.toString()
    }
}
