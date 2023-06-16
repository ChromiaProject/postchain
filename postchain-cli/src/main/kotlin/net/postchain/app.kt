// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import net.postchain.cli.CommandAddBlockchain
import net.postchain.cli.CommandAddConfiguration
import net.postchain.cli.CommandBlockchainReplicaAdd
import net.postchain.cli.CommandBlockchainReplicaRemove
import net.postchain.cli.CommandCheckBlockchain
import net.postchain.cli.CommandDeleteBlockchain
import net.postchain.cli.CommandExportBlockchain
import net.postchain.cli.CommandGenerateContainerZfsInitScript
import net.postchain.cli.CommandListConfigurations
import net.postchain.cli.CommandMustSyncUntil
import net.postchain.cli.CommandPeerInfoAdd
import net.postchain.cli.CommandPeerInfoFind
import net.postchain.cli.CommandPeerInfoImport
import net.postchain.cli.CommandPeerInfoList
import net.postchain.cli.CommandPeerInfoRemove
import net.postchain.cli.CommandRemoveConfiguration
import net.postchain.cli.CommandWipeDb


class Postchain : NoOpCliktCommand(name = "node", help = "Commands to interract directly with the nodes database") {
    init {
        completionOption()
        versionOption(this::class.java.`package`.implementationVersion ?: "(unknown)")
        subcommands(
                CommandAddBlockchain(),
                CommandAddConfiguration(),
                CommandListConfigurations(),
                CommandRemoveConfiguration(),
                CommandBlockchainReplicaAdd(),
                CommandBlockchainReplicaRemove(),
                CommandCheckBlockchain(),
                CommandDeleteBlockchain(),
                CommandExportBlockchain(),
                CommandGenerateContainerZfsInitScript(),
                CommandMustSyncUntil(),
                CommandPeerInfoAdd(),
                CommandPeerInfoFind(),
                CommandPeerInfoImport(),
                CommandPeerInfoList(),
                CommandPeerInfoRemove(),
                CommandWipeDb()
        )
    }
}
