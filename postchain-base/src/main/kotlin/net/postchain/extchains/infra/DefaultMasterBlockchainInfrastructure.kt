package net.postchain.extchains.infra

import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.extchains.api.ExtApiInfrastructure
import net.postchain.extchains.bpm.ExternalBlockchainProcess

class DefaultMasterBlockchainInfrastructure(
        nodeConfigProvider: NodeConfigurationProvider,
        private val syncInfrastructure: MasterSyncInfrastructure,
        private val extApiInfrastructure: ExtApiInfrastructure,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseBlockchainInfrastructure(
        nodeConfigProvider,
        syncInfrastructure,
        extApiInfrastructure,
        nodeDiagnosticContext
), MasterBlockchainInfrastructure {

    override fun makeSlaveBlockchainProcess(chainId: Long, blockchainRid: BlockchainRid, processName: BlockchainProcessName): ExternalBlockchainProcess {
        return syncInfrastructure.makeSlaveBlockchainProcess(chainId, blockchainRid, processName)
                .also(extApiInfrastructure::connectExtProcess)
    }

}