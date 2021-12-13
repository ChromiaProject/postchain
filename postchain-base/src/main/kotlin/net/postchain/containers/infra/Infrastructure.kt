// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerChainDir
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainRid
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.DirectoryDataSource

enum class ContainerResourceType {
    RAM, CPU, STORAGE
}

interface MasterSyncInfra : SynchronizationInfrastructure {

    fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
            containerChainDir: ContainerChainDir
    ): ContainerBlockchainProcess

    fun exitMasterBlockchainProcess(process: ContainerBlockchainProcess)

}

interface MasterBlockchainInfra : BlockchainInfrastructure, MasterSyncInfra

interface SlaveSyncInfra : SynchronizationInfrastructure

