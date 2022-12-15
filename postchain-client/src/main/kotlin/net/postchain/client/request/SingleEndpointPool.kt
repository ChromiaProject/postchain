package net.postchain.client.request

class SingleEndpointPool(url: String) : EndpointPool {
    private val endpoint = Endpoint(url)
    override fun size() = 1
    override fun next() = endpoint
}