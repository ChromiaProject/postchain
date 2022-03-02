package net.postchain.containers.api

import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.debug.NodeDiagnosticContext
import java.net.URL

class DefaultMasterApiInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseApiInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext
), MasterApiInfra {

    override fun connectContainerProcess(process: ContainerBlockchainProcess) {
        if (restApi != null) {
            val nodeConfig = nodeConfigProvider.getConfiguration()

            val path = URL("http",
                    nodeConfig.slaveHost,
                    process.restApiPort,
                    nodeConfig.restApiBasePath
            ).toString()

            val model = HttpExternalModel(path, process.chainId)
            restApi.attachModel(process.blockchainRid.toHex(), model)
        }
    }

    override fun disconnectContainerProcess(process: ContainerBlockchainProcess) {
        restApi?.detachModel(process.blockchainRid.toHex())
    }
}