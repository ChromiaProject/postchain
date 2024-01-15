// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.server.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.StorageInitializer
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.config.app.AppConfig
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvFileReader

class CommandRunNode : CliktCommand(name = "run-node", help = "Starts a node with a configuration") {

    private val nodeConfigFile by nodeConfigOption()

    private val blockchainConfigFile by blockchainConfigOption()
    private val chainIDs by chainIdOption().multiple(listOf(0))
    private val override by option(help = "Overrides the configuration if it exists in database").flag()
    private val update by option(help = "Add the configuration on a height higher than current height if blocks are already build on this chain").flag()

    private val retryTimes by option("-rt", "--retry-times", help = "Number of retries to connect to database").int().default(50)
    private val retryInterval by option("-ri", "--retry-interval", help = "Retry interval for connecting to database (ms)").long().default(1000L)

    private val dumpPid by dumpPidOption()

    init {
        deprecatedDebugOption()
    }

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)

        waitDb(retryTimes, retryInterval, appConfig)
        runStorageCommand(appConfig) {
            StorageInitializer.setupInitialPeers(appConfig, it)
        }

        if (blockchainConfigFile != null) {
            require(chainIDs.size == 1) { "Cannot start more than one chain if a blockchain configuration is specified" }

            val blockchainConfig = GtvFileReader.readFile(blockchainConfigFile!!)
            val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)

            runStorageCommand(appConfig, chainIDs[0], true) { ctx ->
                val wasInitialized = BlockchainApi.initializeBlockchain(ctx, blockchainRid, override, blockchainConfig)
                if (wasInitialized) {
                    appConfig.genesisPeer?.let { DatabaseAccess.of(ctx).addBlockchainReplica(ctx, blockchainRid, PubKey(it.pubKey)) }
                }

                if (!wasInitialized && update) {
                    val currentHeight = BlockchainApi.getLastBlockHeight(ctx)
                    BlockchainApi.addConfiguration(appConfig.pubKey, ctx, currentHeight + 1, false, blockchainConfig)
                }
            }
        }

        if (dumpPid) dumpPid()

        runNode(appConfig, chainIDs)
    }
}
