// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import net.postchain.network.mastersub.master.DefaultMasterCommunicationManager
import net.postchain.network.mastersub.master.MasterCommunicationManager
import net.postchain.network.mastersub.master.MasterConnectionManager
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap


open class DefaultMasterSyncInfra(
        postchainContext: PostchainContext,
        override val masterConnectionManager: MasterConnectionManager,
        private val containerNodeConfig: ContainerNodeConfig,
) : EBFTSynchronizationInfrastructure(postchainContext), MasterSyncInfra {
    private val afterSubnodeCommitListeners = Collections.newSetFromMap(ConcurrentHashMap<AfterSubnodeCommitListener, Boolean>())

    /**
     * We create a new [MasterCommunicationManager] for every new BC process we make.
     */
    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
    ): ContainerBlockchainProcess {

        val communicationManager = DefaultMasterCommunicationManager(
                postchainContext.appConfig,
                nodeConfig,
                containerNodeConfig,
                chainId,
                blockchainRid,
                peersCommConfigFactory,
                connectionManager,
                masterConnectionManager,
                processName,
                afterSubnodeCommitListeners
        ).apply { init() }

        return DefaultContainerBlockchainProcess(
                nodeConfig,
                containerNodeConfig,
                targetContainer.containerPortMapping[containerNodeConfig.subnodeRestApiPort]
                        ?: throw ProgrammerMistake("No port mapping for subnode REST API"),
                processName,
                chainId,
                blockchainRid,
                communicationManager
        )
    }

    override fun exitMasterBlockchainProcess(process: ContainerBlockchainProcess) = Unit

    override fun shutdown() {
        super.shutdown()
        masterConnectionManager.shutdown()
    }

    override fun registerAfterSubnodeCommitListener(afterSubnodeCommitListener: AfterSubnodeCommitListener) {
        afterSubnodeCommitListeners.add(afterSubnodeCommitListener)
    }
}