package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.PostchainClientProvider
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv

class PostTxCommand(private val clientProvider: PostchainClientProvider) : CliktCommand(name = "post-tx", help = "Posts transactions to a postchain node") {

    private val opName by argument(help = "name of the operation to execute.")

    private val args by argument(help = "arguments to pass to the operation.", helpTags = mapOf(
            "integer" to "123",
            "string" to "foo, \"bar\"",
            "bytearray" to "will be encoded using the rell notation x\"<myByteArray>\" and will initially be interpreted as a hex-string.",
            "array" to "[foo,123]",
            "dict" to "{key1->value1,key2->value2}"
    ))
            .multiple()
            .transformAll { args ->
                args.map{ encodeArg(it) }
            }

    private val configFile by configFileOption()

    private val awaitCompleted by option("--await", "-a", help = "Wait for transaction to be included in a block").flag()

    private val cryptoSystem = Secp256K1CryptoSystem()

    override fun run() {
        val config = PostchainClientConfig.fromProperties(configFile.absolutePath)

        runInternal(config, awaitCompleted, opName, *args.toTypedArray())

        println("Tx with the operation has been posted: $opName(${args.joinToString()})")
    }

    internal fun runInternal(config: PostchainClientConfig, awaitConfirmation: Boolean, opName: String, vararg args: Gtv) {
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKeyByteArray, config.privKeyByteArray)
        val client = clientProvider.createClient(config.apiUrl, config.blockchainRid, DefaultSigner(sigMaker, config.pubKeyByteArray), config.statusPollCount)
        val tx = client.makeTransaction()
            .addOperation(opName, *args)
            .sign(sigMaker)
        if (awaitConfirmation) tx.postSyncAwaitConfirmation() else tx.postSync()
    }
}
