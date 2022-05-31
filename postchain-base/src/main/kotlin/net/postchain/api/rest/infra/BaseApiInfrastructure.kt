// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.infra

import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.BaseBlockQueries
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.rest.model.PostchainEBFTModel
import net.postchain.ebft.worker.ValidatorBlockchainProcess

open class BaseApiInfrastructure(
    protected val restApiConfig: RestApiConfig,
    val nodeDiagnosticContext: NodeDiagnosticContext?
) : ApiInfrastructure {

    val restApi: RestApi? = with(restApiConfig) {
        if (port != -1) {
            if (ssl) {
                RestApi(
                    port,
                    basePath,
                    sslCertificate,
                    sslCertificatePassword)
            } else {
                RestApi(
                    port,
                    basePath)
            }
        } else {
            null
        }
    }

    override fun connectProcess(process: BlockchainProcess) {
        if (restApi != null) {
            val engine = process.blockchainEngine
            val apiModel: PostchainModel

            if (process is ValidatorBlockchainProcess) { // TODO: EBFT-specific code, but pretty harmless
                apiModel = PostchainEBFTModel(
                        engine.getConfiguration().chainID,
                        process.nodeStateTracker,
                        process.networkAwareTxQueue,
                        engine.getConfiguration().getTransactionFactory(),
                        engine.getBlockQueries() as BaseBlockQueries, // TODO: [et]: Resolve type cast
                        DefaultDebugInfoQuery(nodeDiagnosticContext)
                )
            } else {
                apiModel = PostchainModel(
                        engine.getConfiguration().chainID,
                        engine.getTransactionQueue(),
                        engine.getConfiguration().getTransactionFactory(),
                        engine.getBlockQueries() as BaseBlockQueries,
                        DefaultDebugInfoQuery(nodeDiagnosticContext)
                )
            }

            restApi.attachModel(bridOf(process), apiModel)
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        restApi?.detachModel(bridOf(process))
    }

    override fun shutdown() {
        restApi?.stop()
    }

    private fun bridOf(process: BlockchainProcess): String {
        return process.blockchainEngine.getConfiguration().blockchainRid.toHex()
    }
}