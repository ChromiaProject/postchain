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
import kotlin.math.min

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

        internal data class ResolvedConcurrency(
                val total: Int,
                val local: Int,
                val external: Int,
                val container: Int,
        )

        /**
         * Pure computation of the four master-mode REST API concurrency values.
         * Takes the environment inputs ([availableProcessors], [databaseSharedReadConcurrency])
         * and the operator-configured values explicitly so it can be unit-tested without
         * mocking the runtime or the Postchain context.
         */
        internal fun resolveConcurrency(
                availableProcessors: Int,
                databaseSharedReadConcurrency: Int,
                configuredTotal: Int,
                configuredLocal: Int,
                configuredExternal: Int,
                configuredContainer: Int,
        ): ResolvedConcurrency {
            val total = resolve(configuredTotal) {
                availableProcessors * 2
            }
            val local = resolve(configuredLocal) {
                // Derive the local pool from CPU/DB, but cap at `total - 1` so the
                // external pool always gets at least one permit. This keeps startup
                // working on low-CPU hosts and when the operator sets only
                // api.request-concurrency without the per-pool splits. The lower
                // bound of 1 avoids accidentally producing 0, which RestApi would
                // interpret as "no limit".
                min(databaseSharedReadConcurrency, 2 * availableProcessors)
                        .coerceAtMost(total - 1)
                        .coerceAtLeast(1)
            }
            val external = resolve(configuredExternal) {
                val value = total - local
                require(value > 0) {
                    "Calculated value for api.request-concurrency.external is invalid ($value). Please check configuration."
                }
                value
            }
            val container = resolve(configuredContainer) {
                val effectiveExternal = if (external > 0) external else total
                max(1, effectiveExternal / CONTAINER_CONCURRENCY_DIVIDER)
            }
            return ResolvedConcurrency(total, local, external, container)
        }

        private inline fun resolve(value: Int, compute: () -> Int): Int =
                if (value == -1 || value > 0) value else compute()
    }

    private var dynamicRequestConcurrency: Int = 0
    private var dynamicRequestConcurrencyLocal: Int = -1
    private var dynamicRequestConcurrencyExternal: Int = -1
    private var dynamicContainerRequestConcurrency: Int = -1

    override fun restApi(restApiConfig: RestApiConfig): RestApi {
        val resolved = resolveConcurrency(
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                databaseSharedReadConcurrency = databaseSharedReadConcurrency,
                configuredTotal = restApiConfig.requestConcurrency,
                configuredLocal = restApiConfig.requestConcurrencyLocal,
                configuredExternal = restApiConfig.requestConcurrencyExternal,
                configuredContainer = restApiConfig.containerRequestConcurrency,
        )
        dynamicRequestConcurrency = resolved.total
        dynamicRequestConcurrencyLocal = resolved.local
        dynamicRequestConcurrencyExternal = resolved.external
        dynamicContainerRequestConcurrency = resolved.container

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
                    process.directoryContainer, maxConnections, restApiConfig.containerRequestTimeoutMs)
            restApi.attachModel(process.blockchainRid, model, process.directoryContainer)
        }
    }

    override fun disconnectContainerProcess(process: ContainerBlockchainProcess) {
        restApi?.detachModel(process.blockchainRid, process.directoryContainer)
    }
}