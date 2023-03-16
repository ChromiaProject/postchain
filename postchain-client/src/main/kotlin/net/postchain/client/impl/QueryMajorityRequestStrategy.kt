package net.postchain.client.impl

import mu.KLogging
import net.postchain.client.bftMajority
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.defaultAsyncHttpHandler
import net.postchain.client.exception.NodesDisagree
import net.postchain.client.request.Endpoint
import net.postchain.client.request.RequestStrategy
import net.postchain.client.request.RequestStrategyFactory
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
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

    override fun <R> request(createRequest: (Endpoint) -> Request,
                             success: (Response) -> R,
                             failure: (Response, Endpoint) -> R,
                             queryMultiple: Boolean): R =
            if (queryMultiple) {
                requestMultiple(createRequest, success, failure)
            } else {
                singleStrategy.request(createRequest, success, failure, false)
            }

    private fun <R> requestMultiple(createRequest: (Endpoint) -> Request,
                                    success: (Response) -> R,
                                    failure: (Response, Endpoint) -> R): R {
        val outcomes = ArrayBlockingQueue<Outcome>(config.endpointPool.size)

        config.endpointPool.forEach { endpoint ->
            val request = createRequest(endpoint)
            asyncHttpClient(request) { response ->
                outcomes.add(if (isSuccess(response.status)) {
                    try {
                        Success(success(response) ?: nullValue)
                    } catch (e: Exception) {
                        Error(e)
                    }
                } else {
                    if (isServerFailure(response.status)) {
                        endpoint.setUnreachable(unreachableDuration(response.status))
                    }
                    try {
                        Failure(failure(response, endpoint) ?: nullValue)
                    } catch (e: Exception) {
                        Error(e)
                    }
                })
            }
        }

        val completedOutcomes = mutableListOf<Outcome>()

        repeat(bftMajorityThreshold) {
            completedOutcomes += outcomes.poll(timeout, TimeUnit.MILLISECONDS)
                    ?: throw TimeoutException("Requests took longer than $timeout ms")
        }

        while (true) {
            val responses = Responses(completedOutcomes.filterIsInstance<Success>().map { it.success })

            if (responses.maxNumberOfEqualResponses() >= bftMajorityThreshold) {
                if (responses.numberOfDistinctResponses() > 1) {
                    logger.warn("Got disagreeing responses, but could still reach BFT majority")
                }
                return fixNull(responses.majorityResponse())
            }

            if ((completedOutcomes.filterIsInstance<NonSuccess>().count()) >= failureThreshold) {
                if (completedOutcomes.filterIsInstance<Failure>().isNotEmpty())
                    return completedOutcomes.filterIsInstance<Failure>().first().fixNull()
                else
                    throw completedOutcomes.filterIsInstance<Error>().first().error
            }

            if (completedOutcomes.size >= config.endpointPool.size) {
                throw NodesDisagree(responses.toString())
            }

            completedOutcomes += outcomes.poll(timeout, TimeUnit.MILLISECONDS)
                    ?: throw TimeoutException("Requests took longer than $timeout ms")
        }
    }

    override fun close() {
        asyncHttpClient.close()
    }

    sealed interface Outcome
    class Success(val success: Any) : Outcome
    sealed class NonSuccess : Outcome
    class Failure(val failure: Any) : NonSuccess() {
        fun <R> fixNull(): R = fixNull(failure)
    }

    class Error(val error: Exception) : NonSuccess()
}

@Suppress("UNCHECKED_CAST")
private fun <R> fixNull(v: Any): R = (if (v === QueryMajorityRequestStrategy.nullValue) null else v) as R

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
            QueryMajorityRequestStrategy(config, httpClient, asyncHttpClient ?: defaultAsyncHttpHandler(config))
}
