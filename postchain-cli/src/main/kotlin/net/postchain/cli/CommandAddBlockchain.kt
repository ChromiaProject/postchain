// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.nodeConfigOption
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

class CommandAddBlockchain : CliktCommand(name = "add-blockchain", help = "Add blockchain") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val blockchainConfigFile by blockchainConfigOption()

    private val force by forceOption().help("Force the addition of already existed blockchain-rid (by chain-id)")


    override fun run() {
        println(
            "add-blockchain will be executed with options: " +
                    ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
        )

        val mode = if (force) AlreadyExistMode.FORCE else AlreadyExistMode.ERROR
        CliExecution.addBlockchain(nodeConfigFile, chainId, blockchainConfigFile, mode)
        println("Configuration has been added successfully")
    }
}