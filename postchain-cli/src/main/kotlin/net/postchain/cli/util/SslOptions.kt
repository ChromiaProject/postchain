package net.postchain.cli.util

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

class SslOptions : OptionGroup(help = "SSL/TLS configuration options") {
    val certChainFile by option(
        "-ccf",
        "--cert-chain-file",
        help = "Certificate chain file",
        envvar = "POSTCHAIN_SERVER_CERTIFICATE"
    )
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .required()

    val privateKeyFile by option(
        "-pkf",
        "--private-key-file",
        help = "Private key file",
        envvar = "POSTCHAIN_SERVER_PRIVATE_KEY"
    )
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .required()
}
