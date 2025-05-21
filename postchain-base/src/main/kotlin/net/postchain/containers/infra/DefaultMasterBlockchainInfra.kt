package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.common.BlockchainRid
import net.postchain.containers.api.MasterApiInfra
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.core.BlockchainState
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

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

    private val afterSubnodeCommitListeners = Collections.newSetFromMap(ConcurrentHashMap<AfterSubnodeCommitListener, Boolean>())

    override fun createMasterBlockchainProcess(
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
            blockchainState: BlockchainState,
            restApiEnabled: Boolean
    ): ContainerBlockchainProcess {
        return masterSyncInfra.createContainerProcess(
                chainId, blockchainRid, dataSource, targetContainer, blockchainState, restApiEnabled, afterSubnodeCommitListeners
        ).also {
            if (restApiEnabled) {
                masterApiInfra.connectContainerProcess(it)
            }
        }
    }

    override fun handleMasterBlockchainProcessExit(process: ContainerBlockchainProcess) {
        if (process.restApiEnabled) {
            masterApiInfra.disconnectContainerProcess(process)
        }
    }

    override fun registerAfterSubnodeCommitListener(afterSubnodeCommitListener: AfterSubnodeCommitListener) {
        afterSubnodeCommitListeners.add(afterSubnodeCommitListener)
    }
}
