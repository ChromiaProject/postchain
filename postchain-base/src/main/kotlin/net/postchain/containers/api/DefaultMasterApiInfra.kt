package net.postchain.containers.api

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.debug.NodeDiagnosticContext
import kotlin.math.max

class DefaultMasterApiInfra(
        restApiConfig: RestApiConfig,
        nodeDiagnosticContext: NodeDiagnosticContext,
        postchainContext: PostchainContext
) : BaseApiInfrastructure(
        restApiConfig,
        nodeDiagnosticContext,
        postchainContext,
), MasterApiInfra {

    companion object : KLogging() {
        private const val CONTAINER_CONCURRENCY_DIVIDER = 4
    }

    private var dynamicRequestConcurrency: Int = 0
    private var dynamicRequestConcurrencyLocal: Int = -1
    private var dynamicRequestConcurrencyExternal: Int = -1
    private var dynamicContainerRequestConcurrency: Int = -1

    override fun restApi(restApiConfig: RestApiConfig): RestApi {

        dynamicRequestConcurrency = getValueOrComputeValue(restApiConfig.requestConcurrency) {
            Runtime.getRuntime().availableProcessors() * 2
        }
        dynamicRequestConcurrencyLocal = getValueOrComputeValue(restApiConfig.requestConcurrencyLocal) {
            calcRequestConcurrency(restApiConfig)
        }
        dynamicRequestConcurrencyExternal = getValueOrComputeValue(restApiConfig.requestConcurrencyExternal) {
            val value = dynamicRequestConcurrency - dynamicRequestConcurrencyLocal
            require(value > 0) {
                "Calculated value for api.request-concurrency.external is invalid ($value). Please check configuration."
            }
            value
        }

        dynamicContainerRequestConcurrency = getValueOrComputeValue(restApiConfig.containerRequestConcurrency) {
            val externalConcurrency = if (dynamicRequestConcurrencyExternal > 0)
                dynamicRequestConcurrencyExternal else dynamicRequestConcurrency
            max(1, externalConcurrency / CONTAINER_CONCURRENCY_DIVIDER)
        }

        with(restApiConfig) {
            return RestApi(
                    listenPort = port,
                    basePath = basePath,
                    nodeDiagnosticContext = nodeDiagnosticContext,
                    gracefulShutdown = gracefulShutdown,
                    requestConcurrency = dynamicRequestConcurrency,
                    requestConcurrencyLocal = dynamicRequestConcurrencyLocal,
                    requestConcurrencyExternal = dynamicRequestConcurrencyExternal,
                    chainRequestConcurrency = chainRequestConcurrency,
                    containerRequestConcurrency = dynamicContainerRequestConcurrency,
                    subnodeHttpRedirect = subnodeHttpRedirect,
                    maxRequestBodySize = maxRequestBodySize,
                    maxDataSize = maxDataSize,
            )
        }
    }


    override fun connectContainerProcess(process: ContainerBlockchainProcess) {
        if (restApi != null) {
            val maxConnections =
                    max(max(dynamicContainerRequestConcurrency, dynamicRequestConcurrencyExternal), dynamicRequestConcurrency)
            val model = HttpExternalModel(restApi.basePath, process.restApiUrl, process.chainId,
                    process.directoryContainer, maxConnections)
            restApi.attachModel(process.blockchainRid, model, process.directoryContainer)
        }
    }

    override fun disconnectContainerProcess(process: ContainerBlockchainProcess) {
        restApi?.detachModel(process.blockchainRid, process.directoryContainer)
    }
}