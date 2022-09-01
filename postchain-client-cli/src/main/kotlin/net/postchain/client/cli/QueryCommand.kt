package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.PostchainClientProvider
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv

class QueryCommand(private val clientProvider: PostchainClientProvider) : CliktCommand(name = "query", help = "Make a query towards a postchain node") {
    private val configFile by configFileOption()
    private val cryptoSystem = Secp256K1CryptoSystem()

    private val queryName by argument(help = "name of the query to make.")

    private val args by argument(help = "arguments to pass to the query. The dict is passed either as key-value pairs or as a singe dict element.")
            .multiple()
            .transformAll { createDict(it) }

    private fun createDict(args: List<String>): Gtv {
        return when {
            args.isEmpty() -> gtv(mapOf())
            args.size == 1 && args[0].startsWith("{") && args[0].endsWith("}") -> verifyAndEncodeDict(args[0])
            else -> encodeArg("{${args.joinToString(",")}}")
        }
    }

    private fun verifyAndEncodeDict(arg: String): Gtv {
        return encodeArg(arg)
    }

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(configFile.absolutePath)

        val res = runInternal(clientConfig, queryName, args)
        println("Query $queryName returned \n$res")

    }

    internal fun runInternal(config: PostchainClientConfig, queryName: String, args: Gtv): Gtv {
        if (args !is GtvDictionary) throw IllegalArgumentException("query must be done with named parameters in a dict")
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKeyByteArray, config.privKeyByteArray)
        return clientProvider.createClient(config.apiUrl, config.blockchainRid, DefaultSigner(sigMaker, config.pubKeyByteArray))
                .querySync(queryName, args)
    }
}