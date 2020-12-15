package net.postchain.containers.infra

import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.core.ApiInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext

class DefaultMasterBlockchainInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        private val masterSyncInfra: MasterSyncInfra,
        apiInfrastructure: ApiInfrastructure,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseBlockchainInfrastructure(
        nodeConfigProvider,
        masterSyncInfra,
        apiInfrastructure,
        nodeDiagnosticContext
), MasterBlockchainInfra {

    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid
    ): ContainerBlockchainProcess {
        return masterSyncInfra.makeMasterBlockchainProcess(processName, chainId, blockchainRid)
    }
}