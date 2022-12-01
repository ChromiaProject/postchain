// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.SafeExecutor.runOnChain
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.exception.UserMistake

class CommandRemoveConfiguration : CliktCommand(name = "remove-configuration", help = "Remove configuration at a given height for a blockchain.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val height by option("-h", "--height", help = "Height of configuration to remove").long().required()

    override fun run() {
        runOnChain(nodeConfigFile, chainId) {
            runStorageCommand(nodeConfigFile, chainId) { ctx ->
                val config = BlockchainApi.getConfiguration(ctx, height)
                if (config != null) {
                    try {
                        BlockchainApi.removeConfiguration(ctx, height)
                        println("Removed configuration at height $height")
                    } catch (e: UserMistake) {
                        println(e.message)
                    } catch (e: Exception) {
                        println("Can't remove configuration at height $height due to: ${e.message}")
                    }
                } else {
                    println("Can't find configuration at height: $height")
                }
            }
        }
    }
}