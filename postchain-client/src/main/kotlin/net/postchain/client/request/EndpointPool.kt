package net.postchain.client.request

interface EndpointPool {
    fun size(): Int
    fun next(): Endpoint

    companion object {
        @JvmStatic
        fun singleUrl(url: String) = SingleEndpointPool(url)

        @JvmStatic
        fun default(urls: List<String>) = RandomizedEndpointPool(urls)
    }
}