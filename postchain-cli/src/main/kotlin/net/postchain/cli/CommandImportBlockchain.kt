// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.importexport.ImporterExporter
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey

class CommandImportBlockchain : CliktCommand(name = "import", help = "Import a blockchain from file") {

    private val nodeConfigFile by nodeConfigOption()

    private val configurationsFile by option("--configurations-file", help = "File to import blockchain configurations from")
            .path(mustExist = true, canBeDir = false, canBeFile = true).required()

    private val blocksFile by option("--blocks-file", help = "File to import blocks and transactions from")
            .path(mustExist = true, canBeDir = false, canBeFile = true).required()

    private val incremental by option("--incremental", help = "Import new configurations and blocks to existing blockchain")
            .flag()

    private val chainRef by mutuallyExclusiveOptions(
            chainIdOption(),
            blockchainRidOption(),
            name = "Chain reference"
    ).single().required()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)

        val chainId = when (val chainRef0 = chainRef) {
            is Long -> runStorageCommand(appConfig, chainRef0) {
                DatabaseAccess.of(it).getBlockchainRid(it)
                        ?: throw CliktError("Blockchain not found by IID: $chainRef0")
                chainRef0
            }

            is BlockchainRid -> runStorageCommand(appConfig) {
                DatabaseAccess.of(it).getChainId(it, chainRef0)
                        ?: throw CliktError("Blockchain not found by RID $chainRef0")
            }

            else -> throw CliktError("Blockchain undefined: $chainRef0")
        }

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
