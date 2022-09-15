package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.icmf.IcmfController
import net.postchain.gtx.GTXBlockchainConfiguration

@Suppress("unused")
class IcmfProcessManagerExtension(val postchainContext: PostchainContext) : BlockchainProcessManagerExtension {

    lateinit var icmfController: IcmfController

    fun getIcmfController(process: BlockchainProcess): IcmfController {
        if (!::icmfController.isInitialized) {
            // steal storage from blockchain engine
            // no, I'm not proud of it...
            icmfController = IcmfController(
                    (process.blockchainEngine as BaseBlockchainEngine).storage
            )
        }
        return icmfController
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
            getAnchorSpecialTxExtension(cfg)?.let {
                // Since this configuration has an Anchor extension, let's add the [IcmfReceiver]
                it.connectIcmfController(getIcmfController(process))

                // We steal the [BlockQueries] from the engine, so that the spec TX extension can call the Anchor Module
                it.setBlockQueries(engine.getBlockQueries())
            }

            // connect process to local dispatcher
            getIcmfController(process).localDispatcher.connectChain(cfg.chainID)
        }
    }

    @Synchronized
    override fun disconnectProcess(process: BlockchainProcess) {
        if (::icmfController.isInitialized) {
            icmfController.localDispatcher.disconnectChain(
                    process.blockchainEngine.getConfiguration().chainID
            )
        }
    }

    @Synchronized
    override fun afterCommit(process: BlockchainProcess, height: Long) {
        if (::icmfController.isInitialized) {
            icmfController.localDispatcher.afterCommit(
                    process.blockchainEngine.getConfiguration().chainID,
                    height
            )
        }
    }

    @Synchronized
    override fun shutdown() {
    }

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