// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.base.BlockchainRid
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName

interface MasterSyncInfra : SynchronizationInfrastructure {

    fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid
    ): ContainerBlockchainProcess

}

interface MasterBlockchainInfra : BlockchainInfrastructure, MasterSyncInfra

interface SlaveSyncInfra : SynchronizationInfrastructure

