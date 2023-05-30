package net.postchain.containers.api

import net.postchain.PostchainContext
import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.debug.NodeDiagnosticContext

class DefaultMasterApiInfra(
        restApiConfig: RestApiConfig,
        nodeDiagnosticContext: NodeDiagnosticContext,
        enableDebugApi: Boolean,
        postchainContext: PostchainContext
) : BaseApiInfrastructure(
        restApiConfig,
        nodeDiagnosticContext,
        enableDebugApi,
        postchainContext
), MasterApiInfra {

    override fun connectContainerProcess(process: ContainerBlockchainProcess) {
        if (restApi != null) {
            val model = HttpExternalModel(restApi.basePath, process.restApiUrl, process.chainId)
            restApi.attachModel(process.blockchainRid, model)
        }
    }

    override fun disconnectContainerProcess(process: ContainerBlockchainProcess) {
        restApi?.detachModel(process.blockchainRid)
    }
}