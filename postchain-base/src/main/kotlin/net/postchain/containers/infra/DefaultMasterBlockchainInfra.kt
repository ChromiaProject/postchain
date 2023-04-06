package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.common.BlockchainRid
import net.postchain.containers.api.MasterApiInfra
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener

open class DefaultMasterBlockchainInfra(
        postchainContext: PostchainContext,
        private val masterSyncInfra: MasterSyncInfra,
        private val masterApiInfra: MasterApiInfra,
) : BaseBlockchainInfrastructure(
        masterSyncInfra,
        masterApiInfra,
        postchainContext
), MasterBlockchainInfra {
    override val masterConnectionManager = masterSyncInfra.masterConnectionManager

    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
    ): ContainerBlockchainProcess {
        return masterSyncInfra.makeMasterBlockchainProcess(
                processName, chainId, blockchainRid, dataSource, targetContainer
        ).also(masterApiInfra::connectContainerProcess)
    }

    override fun exitMasterBlockchainProcess(process: ContainerBlockchainProcess) {
        masterApiInfra.disconnectContainerProcess(process)
    }

    override fun registerAfterSubnodeCommitListener(afterSubnodeCommitListener: AfterSubnodeCommitListener) {
        masterSyncInfra.registerAfterSubnodeCommitListener(afterSubnodeCommitListener)
    }
}
