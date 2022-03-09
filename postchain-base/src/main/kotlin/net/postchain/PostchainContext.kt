package net.postchain

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.DefaultNodeDiagnosticContext
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.network.common.ConnectionManager

data class PostchainContext(
        val nodeConfigProvider: NodeConfigurationProvider,
        val connectionManager: ConnectionManager,
        val nodeDiagnosticContext: NodeDiagnosticContext = DefaultNodeDiagnosticContext()
) {
    fun getNodeConfig() = nodeConfigProvider.getConfiguration()

    fun shutDown() {
        connectionManager.shutdown()
        nodeConfigProvider.close()
    }
}