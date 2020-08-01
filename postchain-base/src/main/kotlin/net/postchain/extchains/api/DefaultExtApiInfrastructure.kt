package net.postchain.extchains.api

import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.base.BaseApiInfrastructure
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.extchains.bpm.ContainerBlockchainProcess
import net.postchain.extchains.bpm.ExternalBlockchainProcess
import java.net.URL

class DefaultExtApiInfrastructure(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseApiInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext
), ExtApiInfrastructure {

    override fun connectExtProcess(process: ExternalBlockchainProcess) {
        if (restApi != null) {
            val nodeConfig = nodeConfigProvider.getConfiguration()
            val process0 = process as ContainerBlockchainProcess

            val path = URL("http",
                    nodeConfig.slaveHost,
                    nodeConfig.restApiPort + 10 * process.chainId.toInt(),
                    nodeConfig.restApiBasePath
            ).toString()

            val model = HttpExternalModel(path, process0.chainId)
            restApi.attachModel(process.processName.blockchainRid.toHex(), model)
        }
    }

    override fun disconnectExtProcess(process: ExternalBlockchainProcess) {
        restApi?.detachModel(blockchainRid(process))
    }

    private fun blockchainRid(process: ExternalBlockchainProcess) =
            (process as ContainerBlockchainProcess).processName.blockchainRid.toHex()
}