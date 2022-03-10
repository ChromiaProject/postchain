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

    // TODO: This will generate a new configuration on each call, which is needed for Managed Mode who updates the peer list.
    val nodeConfig get() = nodeConfigProvider.getConfiguration()

    fun shutDown() {
        connectionManager.shutdown()
        nodeConfigProvider.close()
    }
}