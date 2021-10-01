package net.postchain.containers.infra

import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.core.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.MasterApiInfra
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerChainDir
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.DirectoryDataSource
import java.nio.file.Path

open class DefaultMasterBlockchainInfra(
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
            dataSource: DirectoryDataSource,
            containerChainDir: ContainerChainDir,
            restApiPort: Int
    ): ContainerBlockchainProcess {
        return masterSyncInfra.makeMasterBlockchainProcess(
                processName, chainId, blockchainRid, dataSource, containerChainDir, restApiPort
        ).also(masterApiInfra::connectContainerProcess)
    }
}
