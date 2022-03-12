package net.postchain.anchor

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainEngine
import net.postchain.base.Storage
import net.postchain.base.icmf.AnchorIcmfFetcher
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.gtx.GTXBlockchainConfiguration

@Suppress("unused")
class AnchorSynchronizationInfrastructureExtension(val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {

    /**
     * When this method is executed we know an anchor chain is being created/connected (only such a chain would have
     * this extension).
     *
     * 1. Connect this new anchor process to ICMF so it is fed messages
     *    (we need to connect the [AnchorSpecialTxExtension] to a [IcmfReceiver])
     *
     * 2. Create a new anchor-specific Fetcher for the chainId of the new process
     *    (will be used to fetch messages).
     *
     * Note: All other [BlockchainProcess] will feed us data via [IcmfDispatcher]
     */
    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()

        if (cfg is GTXBlockchainConfiguration) {
            getAnchoreSpecialTxExtension(cfg)?.let {
                // Since this configuration has an Anchor extension, let's add the [IcmfReceiver]
                val icmf = process.getIcmfController()
                it.useIcmfReceiver(icmf.icmfReceiver)

                // ICMF will need the correct [IcmfFetcher] to work
                val bbe = engine as BaseBlockchainEngine
                val storage: Storage = bbe.storage
                icmf.setFetcherForListenerChain(cfg.chainID, AnchorIcmfFetcher(storage))

                // We steal the [BlockQueries] from the engine, so that the spec TX extension can call the Anchor Module
                it.setBlockQueries(engine.getBlockQueries())
            }
        }
    }

    /**
     * Hmm, we need to make sure we don't eat any messages that will get lost
     */
    override fun shutdown() {

    }

    /**
     *  (Olle: Hack. Should be a generic function somewhere else?)
     *
     * Note: having more than one [AnchorSpecialTxExtension] tied to the Anchor process would be wrong I guess, but
     * we don't care about that here.
     */
    private fun getAnchoreSpecialTxExtension(cfg: GTXBlockchainConfiguration): AnchorSpecialTxExtension? {
        return cfg.module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is AnchorSpecialTxExtension)
        } as AnchorSpecialTxExtension?
    }
}