package net.postchain.client.request

import net.postchain.client.config.PostchainClientConfig
import org.http4k.core.HttpHandler

fun interface RequestStrategyFactory {
    fun create(config: PostchainClientConfig, httpClient: HttpHandler): RequestStrategy
}
