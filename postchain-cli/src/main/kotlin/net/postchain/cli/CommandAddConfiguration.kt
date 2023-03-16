// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.CommandAddConfiguration.Height.Absolute
import net.postchain.cli.CommandAddConfiguration.Height.Relative
import net.postchain.cli.util.SafeExecutor.runOnChain
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvFileReader

class CommandAddConfiguration : CliktCommand(
        name = "add-configuration",
        help = "Adds a blockchain configuration. All signers in the new configuration must " +
                "exist in the list of added peerInfos. Else flag --allow-unknown-signers must be set."
) {

    private enum class Height {
        Absolute, Relative
    }

    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val height by mutuallyExclusiveOptions(
            option("-h", "--height", help = "Height of configuration").long()
                    .convert { Absolute to it }
                    .validate {
                        require(it.second > 0L) { "must be positive; use add-blockchain command to add configuration at height 0" }
                    },
            option("-fh", "--future-height", help = "Add a configuration this many blocks in the future. (Not compatible with --height)").long()
                    .convert { Relative to it }
                    .validate { require(it.second > 0L) { "must be positive" } }
    ).single().required()

    private val blockchainConfigFile by blockchainConfigOption().required()

    private val force by forceOption().help("Force the addition of blockchain configuration which already exists of specified chain-id at height")

    private val allowUnknownSigners by option("-a", "--allow-unknown-signers", help = "Allow signers that are not in the list of peerInfos.").flag()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)

        val height0 = when (height.first) {
            Absolute -> height.second
            else -> height.second + runStorageCommand(appConfig, chainId) { ctx ->
                BlockchainApi.getLastBlockHeight(ctx)
            }
        }

        val gtv = try {
            GtvFileReader.readFile(blockchainConfigFile)
        } catch (e: Exception) {
            println("Configuration can not be loaded from the file: ${blockchainConfigFile.path}, an error occurred: ${e.message}")
            return
        }

        runOnChain(appConfig, chainId) {
            try {
                CliExecution.addConfiguration(appConfig, gtv, chainId, height0, force, allowUnknownSigners)
                println("Configuration has been added successfully")
            } catch (e: CliException) {
                println(e.message)
            } catch (e: Exception) {
                println("Can't add configuration: ${e.message}")
            }
        }
    }
}