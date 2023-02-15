// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.infra

import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import net.postchain.api.rest.controller.DisabledDebugInfoQuery
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.BaseBlockQueries
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.rest.model.PostchainEBFTModel
import net.postchain.ebft.worker.ValidatorBlockchainProcess

open class BaseApiInfrastructure(
        restApiConfig: RestApiConfig,
        val nodeDiagnosticContext: NodeDiagnosticContext,
        val configurationProvider: BlockchainConfigurationProvider,
        val enableDebugApi: Boolean
) : ApiInfrastructure {

    val restApi: RestApi? = with(restApiConfig) {
        if (port != -1) {
            if (tls) {
                RestApi(
                        port,
                        basePath,
                        tlsCertificate,
                        tlsCertificatePassword, nodeDiagnosticContext)
            } else {
                RestApi(
                        port,
                        basePath, nodeDiagnosticContext = nodeDiagnosticContext)
            }
        } else {
            null
        }
    }

    override fun restartProcess(process: BlockchainProcess) {
        restApi?.retrieveModel(bridOf(process))?.live = false
    }

    override fun connectProcess(process: BlockchainProcess) {
        if (restApi != null) {
            val engine = process.blockchainEngine
            val apiModel: PostchainModel

            val debugInfoQuery = if (enableDebugApi) DefaultDebugInfoQuery(nodeDiagnosticContext) else DisabledDebugInfoQuery()
            if (process is ValidatorBlockchainProcess) { // TODO: EBFT-specific code, but pretty harmless
                apiModel = PostchainEBFTModel(
                        engine.getConfiguration().chainID,
                        process.nodeStateTracker,
                        process.networkAwareTxQueue,
                        engine.getConfiguration().getTransactionFactory(),
                        engine.getBlockQueries() as BaseBlockQueries, // TODO: [et]: Resolve type cast
                        debugInfoQuery,
                        engine.getConfiguration().blockchainRid,
                        configurationProvider,
                        engine.storage
                )
            } else {
                apiModel = PostchainModel(
                        engine.getConfiguration().chainID,
                        engine.getTransactionQueue(),
                        engine.getConfiguration().getTransactionFactory(),
                        engine.getBlockQueries() as BaseBlockQueries,
                        debugInfoQuery,
                        engine.getConfiguration().blockchainRid,
                        configurationProvider,
                        engine.storage
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