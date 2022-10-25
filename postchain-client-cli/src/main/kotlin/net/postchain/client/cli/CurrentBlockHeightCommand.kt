package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClientProvider

class CurrentBlockHeightCommand(private val clientProvider: PostchainClientProvider) : CliktCommand(name = "current-block-height", help = "Query current block height") {
    private val configFile by configFileOption()

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(configFile?.absolutePath)

        val res = runInternal(clientConfig)
        println("Current block height is $res")

    }

    internal fun runInternal(config: PostchainClientConfig): Long {
        return clientProvider.createClient(config).currentBlockHeightSync()
    }
}
