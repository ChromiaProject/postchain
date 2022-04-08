package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.client.AppConfig
import net.postchain.client.core.ConfirmationLevel
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import kotlin.text.Typography.quote

/**
 * Cli test command
 */
class PostTxCommand : CliktCommand(name = "post-tx", help = "Posts tx") {

    private val opName by argument(help = "name of the operation to execute")

    private val args by argument(help = "arguments to pass to the operation.", helpTags = mapOf(
            "integer" to "123",
            "string" to "foo, \"bar\"",
            "bytearray" to "will be encoded using the rell notation x\"<myByteArray>\" and will initially be interpreted as a hex-string.",
    ))
            .multiple()
            .transformAll { args ->
                args.flatMap { it.split(" ").map { arg -> encodeArg(arg) } }
            }

    private val configFile by configFileOption()

    private val cryptoSystem = SECP256K1CryptoSystem()

    override fun run() {
        try {
            val appConfig = AppConfig.fromProperties(configFile.absolutePath)

            postTx(appConfig) {
                it.addOperation(opName, *args.toTypedArray())
            }

            println("Tx with the operation has been posted: $opName(${args.joinToString()})")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Encodes numbers as GtvInteger and strings as GtvString values
     */
    private fun encodeArg(arg: String): Gtv {
        if (arg.startsWith("x\"")) return encodeByteArray(arg.substring(1))
        return arg.toLongOrNull()
                ?.let(::GtvInteger)
                ?: GtvString(arg.trim(quote))
    }

    private fun encodeByteArray(arg: String): Gtv {
        val bytearray = arg.trim(quote)
        return try {
            gtv(bytearray.hexStringToByteArray())
        } catch (e: IllegalArgumentException) {
            gtv(bytearray.toByteArray())
        }
    }

    private fun postTx(appConfig: AppConfig, addOperations: (GTXTransactionBuilder) -> Unit) {
        val nodeResolver = PostchainClientFactory.makeSimpleNodeResolver(appConfig.apiUrl)
        val sigMaker = cryptoSystem.buildSigMaker(appConfig.pubKeyByteArray, appConfig.privKeyByteArray)
        val signer = DefaultSigner(sigMaker, appConfig.pubKeyByteArray)
        val client = PostchainClientFactory.getClient(nodeResolver, appConfig.blockchainRid, signer)
        val txBuilder = client.makeTransaction()
        addOperations(txBuilder)
        txBuilder.sign(sigMaker)
        txBuilder.postSync(ConfirmationLevel.NO_WAIT)
    }
}
