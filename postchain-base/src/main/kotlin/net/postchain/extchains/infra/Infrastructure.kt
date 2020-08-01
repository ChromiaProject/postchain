// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.extchains.infra

import net.postchain.base.BlockchainRid
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.extchains.bpm.ExternalBlockchainProcess

interface MasterSyncInfrastructure : SynchronizationInfrastructure {

    fun makeSlaveBlockchainProcess(
            chainId: Long,
            blockchainRid: BlockchainRid,
            processName: BlockchainProcessName
    ): ExternalBlockchainProcess

}

interface SlaveSyncInfrastructure : SynchronizationInfrastructure

interface MasterBlockchainInfrastructure : BlockchainInfrastructure, MasterSyncInfrastructure

interface SlaveBlockchainInfrastructure : BlockchainInfrastructure, SlaveSyncInfrastructure