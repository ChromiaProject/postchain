package net.postchain.containers.api

import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.config.node.NodeConfig
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.debug.NodeDiagnosticContext
import java.net.URL

class DefaultMasterApiInfra(
        nodeConfig: NodeConfig,
        nodeDiagnosticContext: NodeDiagnosticContext?
) : BaseApiInfrastructure(
        nodeConfig,
        nodeDiagnosticContext
), MasterApiInfra {

    override fun connectContainerProcess(process: ContainerBlockchainProcess) {
        if (restApi != null) {
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