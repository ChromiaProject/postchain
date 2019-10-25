package net.postchain.base

import net.postchain.api.rest.controller.HttpServer
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.common.toHex
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.ebft.rest.model.PostchainEBFTModel
import net.postchain.ebft.worker.AbstractBlockchainProcess

class BaseApiInfrastructure(nodeConfigProvider: NodeConfigurationProvider) : ApiInfrastructure {

    lateinit var httpServer: HttpServer

    val restApi: RestApi? = with(nodeConfigProvider.getConfiguration()) {
        httpServer = HttpServer(restApiPort, restApiSslCertificate, restApiSslCertificatePassword)
        if (restApiPort != -1) {
            if (restApiSsl) {
                RestApi(
                        restApiBasePath,
                        httpServer)
            } else {
                RestApi(
                        restApiBasePath,
                        httpServer)
            }
        } else {
            null
        }
    }

    override fun connectProcess(process: BlockchainProcess) {
        restApi?.run {
            val engine = process.getEngine()

            val apiModel = PostchainEBFTModel(
                    (process as AbstractBlockchainProcess).nodeStateTracker,
                    process.networkAwareTxQueue,
                    engine.getConfiguration().getTransactionFactory(),
                    engine.getBlockQueries() as BaseBlockQueries) // TODO: [et]: Resolve type cast

            attachModel(blockchainRID(process), apiModel)
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        restApi?.detachModel(blockchainRID(process))
    }

    override fun shutdown() {
        httpServer?.stop()
    }

    private fun blockchainRID(process: BlockchainProcess): String {
        return (process.getEngine().getConfiguration() as BaseBlockchainConfiguration) // TODO: [et]: Resolve type cast
                .blockchainRID.toHex()
    }
}