package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long

fun CliktCommand.chainIdOption() =
    option("--chain-id", "-cid", envvar = "POSTCHAIN_CHAIN_ID", help = "Chain ID")
        .long()
        .required()
