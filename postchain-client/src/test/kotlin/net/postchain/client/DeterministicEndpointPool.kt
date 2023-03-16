package net.postchain.client

import net.postchain.client.request.Endpoint
import net.postchain.client.request.EndpointPool

class DeterministicEndpointPool(urls: List<String>) : EndpointPool {
    private val endpoints = urls.map { Endpoint(it) }

    init {
        require(urls.isNotEmpty()) { "Must provide at least one url" }
    }

    override val size: Int = endpoints.size

    override fun iterator(): Iterator<Endpoint> {
        if (endpoints.size == 1) return endpoints.iterator()
        val reachableEndpoints = endpoints.filter { it.isReachable() }
        if (reachableEndpoints.isEmpty()) {
            endpoints.forEach { it.setReachable() }
            return iterator()
        }
        return reachableEndpoints.iterator()
    }
}
