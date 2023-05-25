// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainState
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import net.postchain.network.mastersub.master.MasterConnectionManager

interface MasterSyncInfra : SynchronizationInfrastructure {
    val masterConnectionManager: MasterConnectionManager

    fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
            blockchainState: BlockchainState
    ): ContainerBlockchainProcess

    fun exitMasterBlockchainProcess(process: ContainerBlockchainProcess)

    fun registerAfterSubnodeCommitListener(afterSubnodeCommitListener: AfterSubnodeCommitListener)

}

interface MasterBlockchainInfra : BlockchainInfrastructure, MasterSyncInfra
