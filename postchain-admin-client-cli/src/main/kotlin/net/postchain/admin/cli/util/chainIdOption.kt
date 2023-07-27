package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.common.BlockchainRid

fun CliktCommand.chainIdOptionNullable() =
        option("-cid", "--chain-id", help = "Chain internal ID within a node", envvar = "POSTCHAIN_CHAIN_ID")
                .long()

fun CliktCommand.chainIdOption() = chainIdOptionNullable()
        .required()

fun CliktCommand.blockchainRidOption() =
        option("-brid", "--blockchain-rid", help = "Blockchain RID", envvar = "POSTCHAIN_BRID")
                .convert { BlockchainRid.buildFromHex(it) }

