package net.postchain.client.core

import net.postchain.client.config.PostchainClientConfig

fun interface PostchainClientProvider {
    fun createClient(
            clientConfig: PostchainClientConfig
    ): PostchainClient
}
