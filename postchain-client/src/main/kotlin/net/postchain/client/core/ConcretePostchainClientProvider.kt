package net.postchain.client.core

import net.postchain.client.config.PostchainClientConfig

class ConcretePostchainClientProvider : PostchainClientProvider {
    override fun createClient(clientConfig: PostchainClientConfig) = ConcretePostchainClient(clientConfig)
}
