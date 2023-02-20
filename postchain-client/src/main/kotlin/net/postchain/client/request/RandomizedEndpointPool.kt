package net.postchain.client.request

import net.postchain.client.bftMajority
import java.security.SecureRandom

class RandomizedEndpointPool(urls: List<String>) : EndpointPool {
    private val endpoints = urls.map { Endpoint(it) }
    private val randGenerator = SecureRandom()

    init {
        require(urls.isNotEmpty()) { "Must provide at least one url" }
    }

    override val size: Int = endpoints.size

    override fun iterator(): Iterator<Endpoint> {
        if (endpoints.size == 1) return endpoints.iterator()
        val reachableEndpoints = endpoints.filter { it.isReachable() }
        if (reachableEndpoints.size < bftMajority(size)) {
            endpoints.forEach { it.setReachable() }
            return iterator()
        }
        return reachableEndpoints.shuffled(randGenerator).iterator()
    }
}
