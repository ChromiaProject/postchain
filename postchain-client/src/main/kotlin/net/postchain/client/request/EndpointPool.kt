package net.postchain.client.request

interface EndpointPool : Iterable<Endpoint> {
    val size: Int

    companion object {
        @JvmStatic
        fun singleUrl(url: String) = SingleEndpointPool(sanitizeUrl(url))

        @JvmStatic
        fun default(urls: List<String>) = RandomizedEndpointPool(urls.map(::sanitizeUrl))

        private fun sanitizeUrl(url: String) = url.trimEnd('/')
    }
}
