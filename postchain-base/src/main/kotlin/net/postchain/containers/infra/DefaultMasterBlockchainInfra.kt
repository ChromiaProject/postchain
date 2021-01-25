package net.postchain.containers.infra

import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.MasterApiInfra
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedNodeDataSource
import java.nio.file.Path

class DefaultMasterBlockchainInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        private val masterSyncInfra: MasterSyncInfra,
        private val masterApiInfra: MasterApiInfra,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseBlockchainInfrastructure(
        nodeConfigProvider,
        masterSyncInfra,
        masterApiInfra,
        nodeDiagnosticContext
), MasterBlockchainInfra {

    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: ManagedNodeDataSource,
            chainConfigsDir: Path
    ): ContainerBlockchainProcess {
        return masterSyncInfra.makeMasterBlockchainProcess(
                processName, chainId, blockchainRid, dataSource, chainConfigsDir
        ).also(masterApiInfra::connectContainerProcess)
    }
}
