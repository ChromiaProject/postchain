package net.postchain.client.request

import java.security.SecureRandom

class RandomizedEndpointPool(urls: List<String>) : EndpointPool {
    private val endpoints = urls.map { Endpoint(it) }
    private val size = endpoints.size
    private val randGenerator = SecureRandom()

    init {
        require(urls.isNotEmpty()) { "Must provide at least one url" }
    }

    override fun size() = size
    override fun next(): Endpoint {
        if (size == 1) return endpoints.first()
        val reachableEndpoints = endpoints.filter { it.isReachable() }
        if (reachableEndpoints.isEmpty()) {
            endpoints.forEach { it.setReachable() }
            return next()
        }
        return reachableEndpoints[nextRand(reachableEndpoints.size)]
    }

    private fun nextRand(size: Int) = randGenerator.nextInt(size)
}
