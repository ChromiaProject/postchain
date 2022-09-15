package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.gtx.GTXBlockchainConfiguration

@Suppress("unused")
class AnchorProcessManagerExtension (val postchainContext: PostchainContext) : BlockchainProcessManagerExtension {

    lateinit var clusterAnchorIcmfReceiverFactory: ClusterAnchorIcmfReceiverFactory

    fun getIcmfController(process: BlockchainProcess): ClusterAnchorIcmfReceiverFactory {
        if (!::clusterAnchorIcmfReceiverFactory.isInitialized) {
                // steal storage from blockchain engine
                // no, I'm not proud of it...
            clusterAnchorIcmfReceiverFactory = ClusterAnchorIcmfReceiverFactory(
                    (process.blockchainEngine as BaseBlockchainEngine).storage
            )
        }
        return clusterAnchorIcmfReceiverFactory
    }

    /**
     * Connect process to ICMF:
     * 1. register receiver chain if necessary
     * 2. connect process to local dispatcher
     */
    @Synchronized
    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()

        if (cfg is GTXBlockchainConfiguration) {
            // create receiver when blockchain has anchoring STE
            getAnchorSpecialTxExtension(cfg)?.connectIcmfController(getIcmfController(process))

            // connect process to local dispatcher
            getIcmfController(process).localDispatcher.connectChain(cfg.chainID)
        }
    }

    @Synchronized
    override fun disconnectProcess(process: BlockchainProcess) {
        if (::clusterAnchorIcmfReceiverFactory.isInitialized) {
            clusterAnchorIcmfReceiverFactory.localDispatcher.disconnectChain(
                    process.blockchainEngine.getConfiguration().chainID
            )
        }
    }

    @Synchronized
    override fun afterCommit(process: BlockchainProcess, height: Long) {
        if (::clusterAnchorIcmfReceiverFactory.isInitialized) {
            clusterAnchorIcmfReceiverFactory.localDispatcher.afterCommit(
                    process.blockchainEngine.getConfiguration().chainID,
                    height
            )
        }
    }

    @Synchronized
    override fun shutdown() {    }

    /**
     *
     * Note: having more than one [AnchorSpecialTxExtension] tied to the Anchor process would be wrong I guess, but
     * we don't care about that here.
     */
    private fun getAnchorSpecialTxExtension(cfg: GTXBlockchainConfiguration): AnchorSpecialTxExtension? {
        return cfg.module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is AnchorSpecialTxExtension)
        } as AnchorSpecialTxExtension?
    }
}