package net.postchain.anchor

import net.postchain.base.icmf.IcmfController
import net.postchain.base.icmf.IcmfReceiver
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtx.GTXBlockchainConfiguration
import javax.naming.ConfigurationException

class AnchorSynchronizationInfrastructureExtension(
    val nodeConfigProvider: NodeConfigurationProvider,
    val nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructureExtension {

    /**
     * When this method is executed we know an anchor chain is being created/connected (only such a chain would have
     * this extension).
     *
     * We need to connect this new anchor process to ICMF so it is fed messages
     * (we need to connect the [AnchorSpecialTxExtension] to a [IcmfReceiver])
     *
     * Note: All other [BlockchainProcess] will feed us data via [IcmfDispatcher] TODO: Olle when do we connect?
     */
    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.getEngine()
        val cfg = engine.getConfiguration()

        if (cfg is GTXBlockchainConfiguration) {
            getAnchoreSpecialTxExtension(cfg)?.let {
                // Since this configuration has an Anchor extension, let's add the [IcmfReceiver]
                val pumpStation = cfg.getComponent<IcmfController>("IcmfPumpStation") // Dynamic access
                if (pumpStation != null) {
                    it.useIcmfReceiver(pumpStation.icmfReceiver)
                } else {
                    throw ConfigurationException("Anchor chain must have an IcmfPumpStation set, chain id: ${cfg.chainID}.")
                }
            }
        }
    }

    /**
     * Hmm, we need to make sure we don't eat any messages that will get lost
     */
    override fun shutdown() {

    }

    /**
     * TODO: Olle, Hack. Should be a generic function somewhere else?
     *
     * Note: having more than one [AnchorSpecialTxExtension] tied to the Anchor process would be wrong I guess, but
     * we don't care about that here.
     */
    private fun getAnchoreSpecialTxExtension(cfg: GTXBlockchainConfiguration): AnchorSpecialTxExtension? {
        return cfg.module.getSpecialTxExtensions().firstOrNull {
                ext -> (ext is AnchorSpecialTxExtension)
        } as AnchorSpecialTxExtension?
    }
}