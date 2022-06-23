package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

fun CliktCommand.pubkeyOption() = option("--pubkey", "-k", envvar = "POSTCHAIN_PUBKEY", help = "Public key to add")
    .convert { it.uppercase() }
    .required()
