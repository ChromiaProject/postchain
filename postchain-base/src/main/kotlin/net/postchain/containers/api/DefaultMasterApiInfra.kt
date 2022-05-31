package net.postchain.containers.api

import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.debug.NodeDiagnosticContext
import java.net.URL

class DefaultMasterApiInfra(
    restApiConfig: RestApiConfig,
    nodeDiagnosticContext: NodeDiagnosticContext?,
    private val containerNodeConfig: ContainerNodeConfig
) : BaseApiInfrastructure(
        restApiConfig,
        nodeDiagnosticContext
), MasterApiInfra {

    override fun connectContainerProcess(process: ContainerBlockchainProcess) {
        if (restApi != null) {
            val path = URL("http",
                    containerNodeConfig.slaveHost,
                    process.restApiPort,
                    restApiConfig.basePath
            ).toString()

            val model = HttpExternalModel(path, process.chainId)
            restApi.attachModel(process.blockchainRid.toHex(), model)
        }
    }

    override fun disconnectContainerProcess(process: ContainerBlockchainProcess) {
        restApi?.detachModel(process.blockchainRid.toHex())
    }
}