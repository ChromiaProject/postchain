package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClientProvider
import net.postchain.gtv.Gtv
import net.postchain.gtv.parse.GtvParser

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
                args.map { GtvParser.parse(it) }
            }

    private val configFile by configFileOption()

    private val awaitCompleted by option("--await", "-a", help = "Wait for transaction to be included in a block").flag()

    override fun run() {
        val config = PostchainClientConfig.fromProperties(configFile?.absolutePath)

        runInternal(config, awaitCompleted, opName, *args.toTypedArray())

        println("Tx with the operation has been posted: $opName(${args.joinToString()})")
    }

    internal fun runInternal(config: PostchainClientConfig, awaitConfirmation: Boolean, opName: String, vararg args: Gtv) {
        val client = clientProvider.createClient(config)
        val tx = client.transactionBuilder()
            .addOperation(opName, *args)
        if (awaitConfirmation) tx.postAwaitConfirmation() else tx.post()
    }
}
