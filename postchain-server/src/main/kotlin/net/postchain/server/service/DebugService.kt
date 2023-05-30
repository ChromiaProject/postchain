package net.postchain.server.service

import net.postchain.api.rest.json.JsonFactory
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.server.NodeProvider

class DebugService(private val nodeProvider: NodeProvider) {

    private val gson = JsonFactory.makePrettyJson()

    val nodeDiagnosticContext: NodeDiagnosticContext get() = nodeProvider.get().postchainContext.nodeDiagnosticContext

    fun debugInfo(): String = gson.toJson(nodeDiagnosticContext.format())

}
