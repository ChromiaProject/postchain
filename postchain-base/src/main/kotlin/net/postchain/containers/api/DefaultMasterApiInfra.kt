package net.postchain.containers.api

import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.debug.NodeDiagnosticContext

class DefaultMasterApiInfra(
        restApiConfig: RestApiConfig,
        nodeDiagnosticContext: NodeDiagnosticContext,
        configurationProvider: BlockchainConfigurationProvider,
        enableDebugApi: Boolean
) : BaseApiInfrastructure(
        restApiConfig,
        nodeDiagnosticContext,
        configurationProvider,
        enableDebugApi
), MasterApiInfra {

    override fun connectContainerProcess(process: ContainerBlockchainProcess) {
        if (restApi != null) {
            val model = HttpExternalModel(process.restApiUrl, process.chainId)
            restApi.attachModel(process.blockchainRid.toHex(), model)
        }
    }

    override fun disconnectContainerProcess(process: ContainerBlockchainProcess) {
        restApi?.detachModel(process.blockchainRid.toHex())
    }
}