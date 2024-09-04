package net.postchain

import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.Storage
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.network.common.ConnectionManager

data class PostchainContext(
        val appConfig: AppConfig,
        val nodeConfigProvider: NodeConfigurationProvider,
        val blockBuilderStorage: Storage,
        val sharedStorage: Storage,
        val connectionManager: ConnectionManager,
        val blockQueriesProvider: BlockQueriesProvider,
        val nodeDiagnosticContext: NodeDiagnosticContext,
        val configurationProvider: BlockchainConfigurationProvider
) {
    val cryptoSystem get() = appConfig.cryptoSystem

    fun shutDown() {
        connectionManager.shutdown()
        nodeConfigProvider.close()
        blockBuilderStorage.close()
        sharedStorage.close()
    }
}