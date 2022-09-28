package net.postchain

import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.Storage
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.network.common.ConnectionManager

data class PostchainContext(
        val appConfig: AppConfig,
        val nodeConfigProvider: NodeConfigurationProvider,
        val storage: Storage,
        val connectionManager: ConnectionManager,
        val nodeDiagnosticContext: NodeDiagnosticContext?,
        val chain0QueryProvider: () -> ((String, Gtv) -> Gtv)?
) {

    fun shutDown() {
        connectionManager.shutdown()
        nodeConfigProvider.close()
        storage.close()
    }
}