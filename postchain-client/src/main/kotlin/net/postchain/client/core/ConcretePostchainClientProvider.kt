package net.postchain.client.core

import net.postchain.client.config.PostchainClientConfig

class ConcretePostchainClientProvider : PostchainClientProvider {
    override fun createClient(clientConfig: PostchainClientConfig): PostchainClient {
        return ConcretePostchainClient(clientConfig)
    }
}