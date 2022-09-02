package net.postchain.client.request

import java.security.SecureRandom

class EndpointPool(urls: List<String>) {
    private val endpoints = urls.map { Endpoint(it) }
    private val size = endpoints.size
    private val randGenerator = SecureRandom()

    init {
        require(urls.isNotEmpty()) { "Must provide at least one url" }
    }

    fun next(): Endpoint {
        if (size == 1) return endpoints.first()
        val reachableEndpoints = endpoints.filter { it.isReachable() }
        return reachableEndpoints[nextRand(reachableEndpoints.size)]
    }

    private fun nextRand(size: Int) = randGenerator.nextInt(size)

    companion object {
        @JvmStatic
        fun singeUrlPool(url: String) = EndpointPool(listOf(url))
    }
}
