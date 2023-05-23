// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.StorageBuilder
import net.postchain.base.importexport.ImporterExporter
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey

class CommandImportBlockchain : CliktCommand(name = "import-blockchain", help = "Import a blockchain from file") {

    private val nodeConfigFile by nodeConfigOption()

    private val configurationsFile by option("--configurations-file", help = "File to import blockchain configurations from")
            .path(mustExist = true, canBeDir = false, canBeFile = true).required()

    private val blocksFile by option("--blocks-file", help = "File to import blocks and transactions from")
            .path(mustExist = true, canBeDir = false, canBeFile = true).required()

    private val incremental by option("--incremental", help = "Import new configurations and blocks to existing blockchain")
            .flag()

    private val chainId by chainIdOption().required()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        StorageBuilder.buildStorage(appConfig).use { storage ->
            ImporterExporter.importBlockchain(
                    KeyPair(PubKey(appConfig.pubKeyByteArray), PrivKey(appConfig.privKeyByteArray)),
                    appConfig.cryptoSystem,
                    storage,
                    chainId,
                    configurationsFile,
                    blocksFile,
                    incremental)
        }
    }
}
