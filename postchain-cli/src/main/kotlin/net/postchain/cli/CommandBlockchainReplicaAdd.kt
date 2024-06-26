// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext

class CommandBlockchainReplicaAdd : CliktCommand(name = "add", help = "Add info to system about blockchain replicas. To be used to sync this node.") {

    private val nodeConfigFile by nodeConfigOption()

    private val pubKey by requiredPubkeyOption()

    private val blockchainRID by blockchainRidOption().required()

    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            val added = runStorageCommand(appConfig) { ctx: AppContext ->
                BlockchainApi.addBlockchainReplica(ctx, blockchainRID, pubKey)
            }

            when {
                added -> echo("Blockchain replica added successfully")
                else -> echo("Blockchain replica already exists")
            }
        }
    }
}