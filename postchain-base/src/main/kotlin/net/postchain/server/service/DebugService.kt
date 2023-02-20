package net.postchain.server.service

import net.postchain.debug.NodeDiagnosticContext

class DebugService(private val nodeDiagnosticContext: NodeDiagnosticContext) {


    fun debugInfo(): String = nodeDiagnosticContext.format()
}
