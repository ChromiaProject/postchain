package net.postchain.base.icmf

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtx.GTXBlockchainConfiguration

/**
 *
 */
class IcmfSynchronizationInfrastructureExtension (
    nodeConfigProvider: NodeConfigurationProvider,
    nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructureExtension {

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.getEngine()
        val cfg = engine.getConfiguration()
        if (cfg is GTXBlockchainConfiguration) {
            val ext = cfg.module.getSpecialTxExtensions().find { it is IcmfSpecialTxExtension }
            if (ext != null && ext is IcmfSpecialTxExtension) {
                val blockchainConfig =
                    engine.getConfiguration() as GTXBlockchainConfiguration
            }
        }
        val eventProcessor = IcmfEventProcessor(
            engine.getBlockQueries()
        )
        val txExtensions = blockchainConfig.module.getSpecialTxExtensions()
        for (te in txExtensions) {
            if (te is IcmfSpecialTxExtension) te.useEventProcessor(eventProcessor)
        }
    }

    override fun shutdown() {}

}