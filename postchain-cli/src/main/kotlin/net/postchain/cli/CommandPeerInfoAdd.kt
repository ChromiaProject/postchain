// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.*
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

class CommandPeerInfoAdd : CliktCommand(name = "peerinfo-add", help = "Add peer information to database") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val host by hostOption().required()
    private val port by portOption().required()
    private val pubKey by requiredPubkeyOption()

    private val force by forceOption().help("Force the addition of peerinfo which already exists with the same host:port")

    override fun run() {
        println("peerinfo-add will be executed with options: " +
                ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE))

        val mode = if (force) AlreadyExistMode.FORCE else AlreadyExistMode.ERROR
        // Make all pubkey strings in db upper case. It will then be consistent with package net.postchain.common.
        //with HEX_CHARS = "0123456789ABCDEF"
        val added = CliExecution.peerinfoAdd(nodeConfigFile, host, port, pubKey.uppercase(), mode)
        when {
            added -> println("Peerinfo has been added successfully")
            else -> println("Peerinfo hasn't been added")
        }
    }
}