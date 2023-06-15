package net.postchain.ebft.worker

import net.postchain.base.PeerCommConfiguration
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.CommunicationManager

/**
 * This is a transitional class to hide away clutter from EBFTSynchronizationInfrastructure. There's
 * room for improvement here, for example, peerCommComnfiguration is already part of the
 * communicationManager (If it's a DefaultXCommunicationManager, and so on).
 */
class WorkerContext(
        val blockchainConfiguration: BlockchainConfiguration,
        val engine: BlockchainEngine,
        val communicationManager: CommunicationManager<EbftMessage>,
        val peerCommConfiguration: PeerCommConfiguration,
        val appConfig: AppConfig,
        val nodeConfig: NodeConfig,
        val restartNotifier: BlockchainRestartNotifier,
        val blockchainConfigurationProvider: BlockchainConfigurationProvider,
        val nodeDiagnosticContext: NodeDiagnosticContext
) {
    fun shutdown() {
        engine.shutdown()
        communicationManager.shutdown()
    }
}
