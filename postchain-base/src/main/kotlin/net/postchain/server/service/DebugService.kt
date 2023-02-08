package net.postchain.server.service

import com.google.gson.JsonObject
import net.postchain.api.rest.json.JsonFactory
import net.postchain.debug.NodeDiagnosticContext

class DebugService(private val nodeDiagnosticContext: NodeDiagnosticContext) {

    private val jsonBuilder = JsonFactory.makePrettyJson()

    fun debugInfo(): String =
            JsonObject()
                .apply {
                    nodeDiagnosticContext.getProperties().forEach { (property, value) ->
                        add(property, jsonBuilder.toJsonTree(value))
                    }
                }.let(jsonBuilder::toJson)
}
