// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption


class PostchainNodeCommand : NoOpCliktCommand(name = "node", help = "Commands to interact directly with the nodes database") {
    init {
        completionOption()
        versionOption(this::class.java.`package`.implementationVersion ?: "(unknown)")
        subcommands(
                PeerCommand()
                        .subcommands(
                                CommandPeerInfoAdd(),
                                CommandPeerInfoFind(),
                                CommandPeerInfoImport(),
                                CommandPeerInfoList(),
                                CommandPeerInfoRemove()
                        ),
                BlockchainCommand()
                        .subcommands(
                                CommandAddBlockchain(),
                                CommandCheckBlockchain(),
                                CommandDeleteBlockchain(),
                                CommandExportBlockchain(),
                                CommandImportBlockchain(),
                                CommandAddConfiguration(),
                                CommandListConfigurations(),
                                CommandRemoveConfiguration(),
                                CommandMustSyncUntil(),
                        ),
                ReplicaCommand()
                        .subcommands(
                                CommandBlockchainReplicaAdd(),
                                CommandBlockchainReplicaRemove()
                        ),
                CommandGenerateContainerZfsInitScript(),
                CommandWipeDb()
        )
    }
}
