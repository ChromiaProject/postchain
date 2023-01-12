package net.postchain.client.impl

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.Endpoint
import net.postchain.client.request.RequestStrategy
import net.postchain.client.request.RequestStrategyFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class SingleEndpointRequestStrategy(
        private val config: PostchainClientConfig,
        private val httpClient: HttpHandler) : RequestStrategy {
    override fun <R> request(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R): R {
        var response: Response? = null
        val endpoint = config.endpointPool.iterator().next()
        val request = createRequest(endpoint)
        for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
            response = httpClient(request)
            if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
            when (response.status) {
                Status.OK -> return success(response)

                Status.BAD_REQUEST,
                Status.NOT_FOUND,
                Status.CONFLICT -> return failure(response)

                Status.UNKNOWN_HOST -> return failure(response)

                // else retry same endpoint
            }
            Thread.sleep(config.failOverConfig.attemptInterval.toMillis())
        }
        return failure(response!!)
    }
}

class SingleEndpointRequestStrategyFactory : RequestStrategyFactory {
    override fun create(config: PostchainClientConfig, httpClient: HttpHandler): RequestStrategy =
            SingleEndpointRequestStrategy(config, httpClient)
}
