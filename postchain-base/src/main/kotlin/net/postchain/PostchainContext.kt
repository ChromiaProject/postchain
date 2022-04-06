package net.postchain

import net.postchain.base.Storage
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.network.common.ConnectionManager

data class PostchainContext(
        val nodeConfigProvider: NodeConfigurationProvider,
        val storage: Storage,
        val connectionManager: ConnectionManager,
        val nodeDiagnosticContext: NodeDiagnosticContext?
) {

    val nodeConfig = nodeConfigProvider.getConfiguration()

    fun shutDown() {
        connectionManager.shutdown()
        nodeConfigProvider.close()
        storage.close()
    }
}