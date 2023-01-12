package net.postchain.client.request

import java.security.SecureRandom

class RandomizedEndpointPool(urls: List<String>) : EndpointPool {
    private val endpoints = urls.map { Endpoint(it) }
    private val randGenerator = SecureRandom()

    init {
        require(urls.isNotEmpty()) { "Must provide at least one url" }
    }

    override fun iterator(): Iterator<Endpoint> {
        if (endpoints.size == 1) return endpoints.iterator()
        val reachableEndpoints = endpoints.filter { it.isReachable() }
        if (reachableEndpoints.isEmpty()) {
            endpoints.forEach { it.setReachable() }
            return iterator()
        }
        return reachableEndpoints.shuffled(randGenerator).iterator()
    }
}
