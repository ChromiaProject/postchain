// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.base.BlockchainRid
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.ManagedNodeDataSource
import java.nio.file.Path

interface MasterSyncInfra : SynchronizationInfrastructure {

    fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: ManagedNodeDataSource,
            chainConfigsDir: Path
    ): ContainerBlockchainProcess

}

interface MasterBlockchainInfra : BlockchainInfrastructure, MasterSyncInfra

interface SlaveSyncInfra : SynchronizationInfrastructure

