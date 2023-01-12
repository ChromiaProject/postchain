package net.postchain.client.impl

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.Endpoint
import net.postchain.client.request.RequestStrategy
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class AbortOnErrorRequestStrategy(
        private val config: PostchainClientConfig,
        private val httpClient: HttpHandler) : RequestStrategy {
    override fun <R> request(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R): R {
        var response: Response? = null
        for (j in 1..config.endpointPool.size()) {
            val endpoint = config.endpointPool.next()
            val request = createRequest(endpoint)
            endpoint@ for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                response = httpClient(request)
                if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
                when (response.status) {
                    Status.OK -> return success(response)
                    Status.BAD_REQUEST -> return failure(response)
                    Status.NOT_FOUND -> return failure(response)
                    Status.INTERNAL_SERVER_ERROR -> return failure(response)
                    Status.SERVICE_UNAVAILABLE -> break@endpoint
                    Status.CONNECTION_REFUSED -> break@endpoint
                    Status.UNKNOWN_HOST -> break@endpoint
                    Status.GATEWAY_TIMEOUT -> break@endpoint
                    Status.CLIENT_TIMEOUT -> break@endpoint
                }
                Thread.sleep(config.failOverConfig.attemptInterval.toMillis())
            }
        }
        return failure(response!!)
    }
}
