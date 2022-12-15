package net.postchain.client.impl

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider

class PostchainClientProviderImpl : PostchainClientProvider {
    override fun createClient(clientConfig: PostchainClientConfig): PostchainClient = PostchainClientImpl(clientConfig)
}
