package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.client.PostchainClientConfig
import net.postchain.client.core.ConfirmationLevel
import net.postchain.client.core.GTXTransactionBuilder

class PostTxCommand : CliktCommand(name = "post-tx", help = "Posts transactions to a postchain node") {

    private val opName by argument(help = "name of the operation to execute.")

    private val args by argument(help = "arguments to pass to the operation.", helpTags = mapOf(
            "integer" to "123",
            "string" to "foo, \"bar\"",
            "bytearray" to "will be encoded using the rell notation x\"<myByteArray>\" and will initially be interpreted as a hex-string.",
            "array" to "[foo,123]",
            "dict" to "{key1=value1,key2=value2}"
    ))
            .multiple()
            .transformAll { args ->
                args.flatMap { it.split(" ").map { arg -> encodeArg(arg) } }
            }

    private val configFile by configFileOption()

    private val cryptoSystem = SECP256K1CryptoSystem()

    override fun run() {
        try {
            val config = PostchainClientConfig.fromProperties(configFile.absolutePath)

            postTx(config) {
                it.addOperation(opName, *args.toTypedArray())
            }

            println("Tx with the operation has been posted: $opName(${args.joinToString()})")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun postTx(clientConfig: PostchainClientConfig, addOperations: (GTXTransactionBuilder) -> Unit) {
        val sigMaker = cryptoSystem.buildSigMaker(clientConfig.pubKeyByteArray, clientConfig.privKeyByteArray)
        val client = createClient(cryptoSystem, clientConfig)
        val txBuilder = client.makeTransaction()
        addOperations(txBuilder)
        txBuilder.sign(sigMaker)
        txBuilder.postSync(ConfirmationLevel.NO_WAIT)
    }
}
