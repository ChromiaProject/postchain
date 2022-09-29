package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.DirectoryClusterManagement
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedBlockchainConfiguration

open class AnchorProcessManagerExtension(postchainContext: PostchainContext) : BlockchainProcessManagerExtension {

    private val localDispatcher = ClusterAnchorDispatcher(postchainContext.storage)

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
                localDispatcher.connectReceiver(cfg.chainID, it.icmfReceiver)
                it.clusterManagement = createClusterManagement(cfg)
            }

            // connect process to local dispatcher
            localDispatcher.connectChain(cfg.chainID)
        }
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

    open fun createClusterManagement(configuration: BlockchainConfiguration): ClusterManagement {
        val dataSource = (configuration as ManagedBlockchainConfiguration).dataSource
        return DirectoryClusterManagement(dataSource::query)
    }

    @Synchronized
    override fun disconnectProcess(process: BlockchainProcess) {
        localDispatcher.disconnectChain(
                process.blockchainEngine.getConfiguration().chainID
        )
    }

    @Synchronized
    override fun afterCommit(process: BlockchainProcess, height: Long) {
        localDispatcher.afterCommit(
                process.blockchainEngine.getConfiguration().chainID,
                height
        )
    }

    @Synchronized
    override fun shutdown() {
    }
}
