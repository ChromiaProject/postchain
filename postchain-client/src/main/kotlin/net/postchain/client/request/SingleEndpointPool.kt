package net.postchain.client.request

class SingleEndpointPool(url: String) : EndpointPool {
    private val endpoints = listOf(Endpoint(url))

    override val size: Int = 1

    override fun iterator(): Iterator<Endpoint> = endpoints.iterator()
}
