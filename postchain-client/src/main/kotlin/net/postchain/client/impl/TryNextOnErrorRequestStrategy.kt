package net.postchain.client.impl

import mu.KLogging
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.Endpoint
import net.postchain.client.request.RequestStrategy
import net.postchain.client.request.RequestStrategyFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class TryNextOnErrorRequestStrategy(
        private val config: PostchainClientConfig,
        private val httpClient: HttpHandler) : RequestStrategy {
    companion object : KLogging()

    override fun <R> request(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R): R {
        var response: Response? = null
        for (endpoint in config.endpointPool) {
            val request = createRequest(endpoint)
            endpoint@ for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                response = httpClient(request)
                if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
                when (response.status) {
                    Status.OK -> return success(response)

                    Status.BAD_REQUEST,
                    Status.NOT_FOUND,
                    Status.CONFLICT -> {
                        logger.debug("Got ${response.status} response from ${endpoint.url}, trying next")
                        break@endpoint
                    }

                    Status.INTERNAL_SERVER_ERROR,
                    Status.SERVICE_UNAVAILABLE,
                    Status.UNKNOWN_HOST -> break@endpoint

                    // else retry same endpoint
                }
                Thread.sleep(config.failOverConfig.attemptInterval.toMillis())
            }
        }
        return failure(response!!)
    }
}

class TryNextOnErrorRequestStrategyFactory : RequestStrategyFactory {
    override fun create(config: PostchainClientConfig, httpClient: HttpHandler): RequestStrategy =
            TryNextOnErrorRequestStrategy(config, httpClient)
}
