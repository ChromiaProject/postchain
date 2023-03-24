// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File

class EncodeBlockchainConfigurationCommand : CliktCommand(
        name = "encode-blockchain-config",
        help = "Encodes blockchain configuration in GtxML format into binary format (GTV)"
) {

    private val blockchainConfigFilename by option(
            "-bc", "--blockchain-config",
            help = "Configuration file of blockchain (GtxML)"
    ).required()

    override fun run() {
        println("GtxML file will be encoded to binary: $blockchainConfigFilename")

        try {
            val gtv = GtvMLParser.parseGtvML(File(blockchainConfigFilename).readText())

            // bin-gtv
            val binGtvFilename = File(blockchainConfigFilename).nameWithoutExtension + ".gtv"
            val binGtv = GtvEncoder.encodeGtv(gtv)
            File(binGtvFilename).writeBytes(binGtv)
            println("Binary file has been created: $binGtvFilename")

            // brid
            val bridFilename = "brid.txt"
            val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(gtv, ::sha256Digest)
            File(bridFilename).writeText(blockchainRid.toHex())
            println("Brid file has been created: brid.txt")

        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
