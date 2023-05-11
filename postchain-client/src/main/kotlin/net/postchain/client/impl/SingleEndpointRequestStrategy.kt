package net.postchain.client.impl

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.Endpoint
import net.postchain.client.request.RequestStrategy
import net.postchain.client.request.RequestStrategyFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response

class SingleEndpointRequestStrategy(
        private val config: PostchainClientConfig,
        httpClient: HttpHandler) : SynchronousRequestStrategy(httpClient) {
    override fun <R> request(createRequest: (Endpoint) -> Request,
                             success: (Response, Endpoint) -> R,
                             failure: (Response, Endpoint) -> R,
                             queryMultiple: Boolean): R {
        var response: Response? = null
        val endpoint = config.endpointPool.iterator().next()
        val request = createRequest(endpoint)
        for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
            response = makeRequest(request)
            when {
                isSuccess(response.status) -> return success(response, endpoint)

                isClientFailure(response.status) -> return failure(response, endpoint)

                isServerFailure(response.status) -> {
                    endpoint.setUnreachable(unreachableDuration(response.status))
                    return failure(response, endpoint)
                }

                // else retry same endpoint
            }
            Thread.sleep(config.failOverConfig.attemptInterval.toMillis())
        }
        return failure(response!!, endpoint)
    }

    override fun close() {}
}

class SingleEndpointRequestStrategyFactory : RequestStrategyFactory {
    override fun create(config: PostchainClientConfig, httpClient: HttpHandler): RequestStrategy =
            SingleEndpointRequestStrategy(config, httpClient)
}
