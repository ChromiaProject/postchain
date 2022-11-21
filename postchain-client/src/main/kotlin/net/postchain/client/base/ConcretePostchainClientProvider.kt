package net.postchain.client.base

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider

class ConcretePostchainClientProvider : PostchainClientProvider {
    override fun createClient(clientConfig: PostchainClientConfig): PostchainClient = ConcretePostchainClient(clientConfig)
}
