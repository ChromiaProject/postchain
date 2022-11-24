// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandAddConfiguration : CliktCommand(name = "add-configuration", help = "Adds a blockchain configuration. All signers in the new configuration must " +
        "exist in the list of added peerInfos. Else flag --allow-unknown-signers must be set.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val height by option("-h", "--height", help = "Height of configuration").long().default(0)

    private val futureHeight by option("-fh", "--future-height", help = "Add a configuration this many blocks in the future. (Not compatible with --height)").long().default(0)

    private val blockchainConfigFile by blockchainConfigOption()

    private val force by forceOption().help("Force the addition of blockchain configuration " +
            "which already exists of specified chain-id at height")

    private val allowUnknownSigners by option("-a", "--allow-unknown-signers", help = "Allow signers that are not in the list of peerInfos.").flag()

    override fun run() {
        if (height != 0L && futureHeight != 0L) {
            throw IllegalArgumentException("Cannot use --height and --future at the same time")
        }

        val mode = if (force) AlreadyExistMode.FORCE else AlreadyExistMode.ERROR
        var heightToUse = height
        if (futureHeight > 0) {
            runStorageCommand(AppConfig.fromPropertiesFile(nodeConfigFile), chainId) { ctx ->
                heightToUse = BlockchainApi.getLastBlockHeight(ctx) + futureHeight
            }
        }
        CliExecution.addConfiguration(nodeConfigFile, blockchainConfigFile, chainId, heightToUse, mode, allowUnknownSigners)
        println("Configuration has been added successfully")
    }

}