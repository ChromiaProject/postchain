// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import org.bitcoinj.crypto.MnemonicCode
import java.io.FileOutputStream
import java.util.*


class CommandKeygen : CliktCommand(name = "keygen", help = "Generates public/private key pair") {

    private val wordList by option(
        "-m", "--mnemonic",
        help = """
            Mnemonic word list, words separated by space, e.g:
                "lift employ roast rotate liar holiday sun fever output magnet...""
        """.trimIndent()
    )
        .default("")

    private val file by option("-s", "--save", help = "File to save the generated keypair in")

    /**
     * Cryptographic key generator. Will generate a pair of public and private keys and print to stdout.
     */
    override fun run() {
        val cs = Secp256K1CryptoSystem()
        var privKey = cs.generatePrivKey().data
        val mnemonicInstance = MnemonicCode.INSTANCE
        var mnemonic = mnemonicInstance.toMnemonic(privKey).joinToString(" ")
        if (wordList.isNotEmpty()) {
            val words = wordList.split(" ")
            mnemonicInstance.check(words)
            mnemonic = wordList
            privKey = mnemonicInstance.toEntropy(words)
        }

        val pubKey = secp256k1_derivePubKey(privKey)

        file?.let { fileName ->
            val properties = Properties()
            properties["privkey"] = privKey.toHex()
            properties["pubkey"] = pubKey.toHex()

            FileOutputStream(fileName).use { fs ->
                properties.store(fs, "Keypair generated using secp256k1")
                fs.flush()
            }
        }
        println(
            """
            |privkey:   ${privKey.toHex()}
            |pubkey:    ${pubKey.toHex()}
            |mnemonic:  $mnemonic 
        """.trimMargin()
        )
    }


}
