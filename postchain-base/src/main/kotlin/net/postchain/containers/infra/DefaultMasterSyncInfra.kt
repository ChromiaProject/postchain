// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.PostchainContext
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.common.BlockchainRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.master.DefaultMasterCommunicationManager
import net.postchain.network.mastersub.master.MasterCommunicationManager
import net.postchain.network.mastersub.master.MasterConnectionManager
import java.nio.file.Path


open class DefaultMasterSyncInfra(
        postchainContext: PostchainContext,
        protected val masterConnectionManager: MasterConnectionManager,
        private val containerNodeConfig: ContainerNodeConfig
) : EBFTSynchronizationInfrastructure(postchainContext), MasterSyncInfra {

    /**
     * We create a new [MasterCommunicationManager] for every new BC process we make.
     */
    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
            containerChainDir: Path
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
                dataSource,
                processName
        ).apply { init() }

        return DefaultContainerBlockchainProcess(
                nodeConfig,
                processName,
                chainId,
                blockchainRid,
                targetContainer.restApiPort,
                communicationManager,
                dataSource,
                containerChainDir,
        )
    }

    override fun exitMasterBlockchainProcess(process: ContainerBlockchainProcess) = Unit

    override fun shutdown() {
        super.shutdown()
        masterConnectionManager.shutdown()
    }
}