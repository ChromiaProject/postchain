// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.core.BlockchainState
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedEBFTSynchronizationInfrastructure
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import net.postchain.network.mastersub.master.DefaultMasterCommunicationManager
import net.postchain.network.mastersub.master.MasterCommunicationManager
import net.postchain.network.mastersub.master.MasterConnectionManager

open class DefaultMasterSyncInfra(
        postchainContext: PostchainContext,
        override val masterConnectionManager: MasterConnectionManager,
        private val containerNodeConfig: ContainerNodeConfig,
) : ManagedEBFTSynchronizationInfrastructure(postchainContext), MasterSyncInfra {

    /**
     * We create a new [MasterCommunicationManager] for every new BC process we make.
     */
    override fun createContainerProcess(
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
            blockchainState: BlockchainState,
            restApiEnabled: Boolean,
            afterSubnodeCommitListeners: Set<AfterSubnodeCommitListener>
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
                afterSubnodeCommitListeners
        )

        return DefaultContainerBlockchainProcess(
                nodeConfig,
                containerNodeConfig,
                restApiEnabled,
                restApiPort = targetContainer.containerPortMapping[containerNodeConfig.subnodeRestApiPort]
                        ?: throw ProgrammerMistake("No port mapping for subnode REST API"),
                chainId,
                blockchainRid,
                blockchainState,
                targetContainer.containerName.directoryContainer,
                communicationManager
        )
    }

    override fun shutdown() {
        super.shutdown()
        masterConnectionManager.shutdown()
    }
}