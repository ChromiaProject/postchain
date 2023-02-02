package net.postchain.client.impl

import mu.KLogging
import net.postchain.client.bftMajority
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.exception.NodesDisagree
import net.postchain.client.request.Endpoint
import net.postchain.client.request.RequestStrategy
import net.postchain.client.request.RequestStrategyFactory
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheAsyncClient
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException

class QueryMajorityRequestStrategy(
        private val config: PostchainClientConfig,
        httpClient: HttpHandler,
        private val asyncHttpClient: AsyncHttpHandler) : RequestStrategy {

    companion object : KLogging() {
        val nullValue = Any()
    }

    private val singleStrategy = TryNextOnErrorRequestStrategy(config, httpClient)
    private val bftMajorityThreshold = bftMajority(config.endpointPool.size)
    private val failureThreshold = config.endpointPool.size - bftMajorityThreshold + 1
    private val timeout = config.connectTimeout.toMillis() + config.responseTimeout.toMillis()

    override fun <R> request(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R, queryMultiple: Boolean): R =
            if (queryMultiple) {
                requestMultiple(createRequest, success, failure)
            } else {
                singleStrategy.request(createRequest, success, failure, false)
            }

    private fun <R> requestMultiple(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R): R {
        val semaphore = Semaphore(0)

        val successes = ConcurrentHashMap<String, Any>(config.endpointPool.size)
        val failures = ConcurrentHashMap<String, Any>(config.endpointPool.size)
        val errors = ConcurrentHashMap<String, Exception>(config.endpointPool.size)

        config.endpointPool.forEach { endpoint ->
            val request = createRequest(endpoint)
            asyncHttpClient(request) { response ->
                if (isSuccess(response.status)) {
                    try {
                        successes[endpoint.url] = success(response) ?: nullValue
                    } catch (e: Exception) {
                        errors[endpoint.url] = e
                    }
                } else {
                    if (isServerFailure(response.status)) {
                        endpoint.setUnreachable(unreachableDuration(response.status))
                    }
                    try {
                        failures[endpoint.url] = failure(response) ?: nullValue
                    } catch (e: Exception) {
                        errors[endpoint.url] = e
                    }
                }
                semaphore.release()
            }
        }

        val startTime = System.currentTimeMillis()

        semaphore.acquire(bftMajorityThreshold)

        while (true) {
            val responses = Responses(successes.values)

            if (responses.maxNumberOfEqualResponses() >= bftMajorityThreshold) {
                if (responses.numberOfDistinctResponses() > 1) {
                    logger.warn("Got disagreeing responses, but could still reach BFT majority")
                }
                return fixNull(responses.majorityResponse())
            }

            if ((failures.size + errors.size) >= failureThreshold) {
                if (failures.isNotEmpty())
                    return fixNull(failures.values.first())
                else
                    throw errors.values.first()
            }

            if ((successes.size + failures.size + errors.size) >= config.endpointPool.size) {
                throw NodesDisagree(responses.toString())
            }

            if (System.currentTimeMillis() - startTime > timeout) throw TimeoutException("Requests took longer than $timeout ms")

            semaphore.acquire()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> fixNull(v: Any): R = (if (v === nullValue) null else v) as R

    override fun close() {
        asyncHttpClient.close()
    }
}

class Responses(successResponses: Collection<Any>) {
    private val distinctResponses: List<Map.Entry<Any, Int>> =
            successResponses.groupingBy { it }.eachCount().entries.sortedBy { it.value }.reversed()

    fun maxNumberOfEqualResponses(): Int = distinctResponses.firstOrNull()?.value ?: 0

    fun numberOfDistinctResponses(): Int = distinctResponses.size

    fun majorityResponse() = distinctResponses.first().key

    override fun toString(): String = distinctResponses.toString()
}

class QueryMajorityRequestStrategyFactory(private val asyncHttpClient: AsyncHttpHandler? = null) : RequestStrategyFactory {
    override fun create(config: PostchainClientConfig, httpClient: HttpHandler): RequestStrategy =
            QueryMajorityRequestStrategy(config, httpClient, asyncHttpClient
                    ?: ApacheAsyncClient(HttpAsyncClients.custom()
                            .setDefaultRequestConfig(RequestConfig.custom()
                                    .setRedirectsEnabled(false)
                                    .setCookieSpec(StandardCookieSpec.IGNORE)
                                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.connectTimeout.toMillis()))
                                    .setResponseTimeout(Timeout.ofMilliseconds(config.responseTimeout.toMillis()))
                                    .build()).build().apply { start() }))
}
