// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option

/**
 * Root Cli object
 */
class Cli : CliktCommand(name = "postchain-devtools") {

    val nodeConfig by option(
            "--node-config", "-nc",
            help = "Path to node config file")

    val blockchainConfig by option(
            "--blockchain-config", "-bc",
            help = "Path to blockchain config file")

    override fun run() {}
}