// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.infra

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.DebugApi
import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.core.ApiInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.rest.model.PostchainEBFTModel
import net.postchain.ebft.worker.ValidatorBlockchainProcess

open class BaseApiInfrastructure(
        restApiConfig: RestApiConfig,
        val nodeDiagnosticContext: NodeDiagnosticContext,
        private val postchainContext: PostchainContext
) : ApiInfrastructure {

    companion object : KLogging()

    val restApi: RestApi? = with(restApiConfig) {
        if (port != -1) {
            logger.info { "Starting REST API on port $port and path $basePath/" }
            try {
                RestApi(
                        listenPort = port,
                        basePath = basePath,
                        nodeDiagnosticContext = nodeDiagnosticContext,
                        gracefulShutdown = restApiConfig.gracefulShutdown,
                        requestConcurrency = restApiConfig.requestConcurrency,
                        chainRequestConcurrency = restApiConfig.chainRequestConcurrency
                )
            } catch (e: Exception) {
                logger.error("Unable to start REST API on port $port", e)
                throw e
            }
        } else {
            null
        }
    }

    val debugApi: DebugApi? = if (restApiConfig.debugPort != -1) {
        logger.info { "Starting Debug API on port ${restApiConfig.debugPort}" }
        try {
            DebugApi(
                    listenPort = restApiConfig.debugPort,
                    debugInfoQuery = DefaultDebugInfoQuery(nodeDiagnosticContext),
                    gracefulShutdown = restApiConfig.gracefulShutdown
            )
        } catch (e: Exception) {
            logger.error("Unable to start Debug API on port ${restApiConfig.debugPort}", e)
            throw e
        }
    } else {
        null
    }

    override fun restartProcess(process: BlockchainProcess) {
        restApi?.retrieveModel(bridOf(process))?.live = false
    }

    override fun connectProcess(process: BlockchainProcess) {
        if (restApi != null) {
            val engine = process.blockchainEngine
            val apiModel: PostchainModel

            val blockchainConfiguration = engine.getConfiguration()
            val blockchainRid = blockchainConfiguration.blockchainRid
            val diagnosticData = nodeDiagnosticContext.blockchainData(blockchainRid)
            val queryCacheTtlSeconds =
                    (blockchainConfiguration as? BaseBlockchainConfiguration)?.configData?.queryCacheTtlSeconds
                            ?: 0
            if (process is ValidatorBlockchainProcess) { // TODO: EBFT-specific code, but pretty harmless
                apiModel = PostchainEBFTModel(
                        blockchainConfiguration,
                        process.networkAwareTxQueue,
                        engine.getBlockQueries(),
                        blockchainRid,
                        engine.sharedStorage,
                        postchainContext,
                        diagnosticData,
                        queryCacheTtlSeconds
                )
            } else {
                apiModel = PostchainModel(
                        blockchainConfiguration,
                        engine.getTransactionQueue(),
                        engine.getBlockQueries(),
                        blockchainRid,
                        engine.sharedStorage,
                        postchainContext,
                        diagnosticData,
                        queryCacheTtlSeconds
                )
            }

            restApi.attachModel(bridOf(process), apiModel)
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        restApi?.detachModel(bridOf(process))
    }

    override fun shutdown() {
        restApi?.close()
        debugApi?.close()
    }

    private fun bridOf(process: BlockchainProcess): BlockchainRid {
        return process.blockchainEngine.getConfiguration().blockchainRid
    }
}