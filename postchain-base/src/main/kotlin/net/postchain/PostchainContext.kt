package net.postchain

import net.postchain.base.Storage
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.network.common.ConnectionManager
import net.postchain.debug.NodeDiagnosticContext

data class PostchainContext(
        val appConfig: AppConfig,
        val nodeConfigProvider: NodeConfigurationProvider,
        val storage: Storage,
        val connectionManager: ConnectionManager,
        val nodeDiagnosticContext: NodeDiagnosticContext?
) {

    fun shutDown() {
        connectionManager.shutdown()
        nodeConfigProvider.close()
        storage.close()
    }
}