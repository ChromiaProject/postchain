// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.StorageBuilder
import net.postchain.base.importexport.ImporterExporter
import net.postchain.cli.CliExecution.resolveChainId
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandExportBlockchain : CliktCommand(name = "export", help = "Export a blockchain to file") {

    private val nodeConfigFile by nodeConfigOption()

    private val configurationsFile by option("--configurations-file", help = "File to export blockchain configurations to")
            .path(mustExist = false, canBeDir = false).required()

    private val blocksFile by option("--blocks-file", help = "File to export blocks and transactions to")
            .path(mustExist = false, canBeDir = false)

    private val overwrite by option("--overwrite", help = "Overwrite existing files")
            .flag()

    private val fromHeight by option("--from-height",
            help = "Only export configurations and blocks from and including this height (will start from first block by default)")
            .long().default(0L)

    private val upToHeight by option("--up-to-height",
            help = "Only export configurations and blocks up to and including this height (will continue to last block by default)")
            .long().default(Long.MAX_VALUE)

    private val chainRef by mutuallyExclusiveOptions(
            chainIdOption(),
            blockchainRidOption(),
            name = "Chain reference"
    ).single().required()

    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            val chainId = resolveChainId(appConfig, chainRef)
            StorageBuilder.buildStorage(appConfig, allowUpgrade = false).use { storage ->
                ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, overwrite,
                        fromHeight = fromHeight, upToHeight = upToHeight)
            }
        }
    }
}
