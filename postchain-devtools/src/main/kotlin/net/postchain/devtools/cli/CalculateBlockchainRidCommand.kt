// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.common.GtvToBlockchainRidFactory
import java.io.File

class CalculateBlockchainRidCommand : CliktCommand(
        name = "blockchain-rid",
        help = "Calculates blockchain RID by blockchain configuration in GtxML format"
) {

    private val blockchainConfigFilename by option(
            names = *arrayOf("-bc", "--blockchain-config"),
            help = "Configuration file of blockchain (GtvML (*.xml) or Gtv (*.gtv))"
    ).required()

    override fun run() {
        println("Blockchain RID will be calculated for: $blockchainConfigFilename")

        try {
            val gtv = GtvMLParser.parseGtvML(File(blockchainConfigFilename).readText())
            val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(gtv)

            println("Blockchain RID: $blockchainRid")

        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
