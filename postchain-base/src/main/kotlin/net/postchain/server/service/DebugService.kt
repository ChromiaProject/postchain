package net.postchain.server.service

import net.postchain.debug.NodeDiagnosticContext
import net.postchain.server.NodeProvider

class DebugService(private val nodeProvider: NodeProvider) {

    val nodeDiagnosticContext: NodeDiagnosticContext get() = nodeProvider.get().postchainContext.nodeDiagnosticContext

    fun debugInfo(): String = nodeDiagnosticContext.format()

}
