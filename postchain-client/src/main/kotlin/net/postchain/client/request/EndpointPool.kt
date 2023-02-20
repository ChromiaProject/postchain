package net.postchain.client.request

interface EndpointPool : Iterable<Endpoint> {
    val size: Int

    companion object {
        @JvmStatic
        fun singleUrl(url: String) = SingleEndpointPool(url)

        @JvmStatic
        fun default(urls: List<String>) = RandomizedEndpointPool(urls)
    }
}
