// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.server.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import mu.KLogging
import net.postchain.StorageInitializer
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFileReader
import java.io.File
import java.nio.file.Paths

class CommandRunNodeAuto : CliktCommand(name = "run-node-auto", help = "Run Node Auto") {

    companion object : KLogging()

    /**
     * Configuration directory structure:
     *
     *  config/
     *      node-config.properties
     *      blockchains/
     *          1/
     *              0.conf.xml
     *              1.conf.xml
     *              ...
     *          2/
     *              ...
     */
    private val configDirectory by option("-d", "--directory", help = "Configuration directory").default(".")

    private val debug by debugOption()
    private val dumpPid by dumpPidOption()

    private val NODE_CONFIG_FILE = "node-config.properties"
    private val BLOCKCHAIN_DIR = "blockchains"

    private val lastHeights = mutableMapOf<Long, Long>() // { chainId -> height }

    override fun run() {
        val chainsDir = Paths.get(configDirectory, BLOCKCHAIN_DIR).toFile()
        val nodeConfigFile = Paths.get(configDirectory, NODE_CONFIG_FILE).toFile()
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile, debug)

        waitDb(50, 1000, appConfig)
        runStorageCommand(appConfig) {
            StorageInitializer.setupInitialPeers(appConfig, it)
        }
        val chainIds = loadChainsConfigs(chainsDir, appConfig)
        if (dumpPid) dumpPid()
        runNode(appConfig, chainIds.sorted())
        println("Postchain node is running")
    }

    /**
     * Loads configs of chains into DB
     * @return list of ids of found chains
     */
    private fun loadChainsConfigs(chainsDir: File, appConfig: AppConfig): List<Long> {
        val chainIds = mutableListOf<Long>()

        if (chainsDir.exists()) {
            chainsDir.listFiles()
                    ?.filter(File::isDirectory)
                    ?.forEach dirs@{ dir ->
                        val chainId = dir.name.toLong()
                        chainIds.add(chainId)

                        val getHeight = { file: File ->
                            file.nameWithoutExtension.substringBefore(".").toLong()
                        }
                        val configs = mutableMapOf<Long, File>()
                        dir.listFiles()?.filter { it.extension == "xml" }?.associateByTo(configs, getHeight)
                        dir.listFiles()?.filter { it.extension == "gtv" }?.associateByTo(configs, getHeight)

                        val chainExists = findBlockchainRid(appConfig, chainId) != null
                        if (!chainExists && configs.isNotEmpty() && !configs.containsKey(0L)) {
                            val configsCsv = configs.toSortedMap().values.joinToString(separator = ", ") { it.path }
                            println("Can't find blockchain by chainId: $chainId, configs will not be added: $configsCsv")
                            return@dirs
                        }

                        lastHeights[chainId] = if (chainExists) {
                            runStorageCommand(appConfig, chainId, true) { ctx: EContext ->
                                BlockchainApi.getLastBlockHeight(ctx)
                            }
                        } else -1L

                        run {
                            configs.filterKeys { it > (lastHeights[chainId] ?: -1) }
                                    .toSortedMap()
                                    .forEach configs@{ (height, blockchainConfigFile) ->
                                        val gtv = try {
                                            GtvFileReader.readFile(blockchainConfigFile)
                                        } catch (e: Exception) {
                                            println("Configuration for chain $chainId can not be loaded at height $height " +
                                                    "from the file ${blockchainConfigFile.path}, an error occurred: ${e.message}")
                                            if (height == 0L) return@run
                                            else return@configs
                                        }

                                        if (height == 0L) {
                                            try {
                                                addBlockchain(appConfig, chainId, gtv)
                                            } catch (e: CliException) {
                                                println(e.message)
                                                return@run
                                            } catch (e: Exception) {
                                                println("Can't add configuration: $e")
                                                return@run
                                            }

                                        } else {
                                            try {
                                                addConfiguration(appConfig, gtv, chainId, height.toLong())
                                                logger.info { "Chain (chainId: $chainId) configuration at height $height has been added" }
                                                println("Configuration has been added successfully")
                                            } catch (e: CliException) {
                                                println(e.message)
                                            } catch (e: Exception) {
                                                println("Can't add configuration: $e")
                                            }
                                        }
                                    }
                        }

                    }
        }
        return chainIds
    }

    private fun findBlockchainRid(appConfig: AppConfig, chainId: Long): BlockchainRid? {
        return runStorageCommand(appConfig, chainId, true) { ctx: EContext ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)
        }
    }

    private fun addBlockchain(
            appConfig: AppConfig,
            chainId: Long,
            blockchainConfig: Gtv
    ): BlockchainRid {
        // If brid is specified in nodeConfigFile, use that instead of calculating it from blockchain configuration.
        val keyString = "brid.chainid.$chainId"
        val brid = if (appConfig.containsKey(keyString)) BlockchainRid.buildFromHex(appConfig.getString(keyString)) else
            GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)

        return runStorageCommand(appConfig, chainId, true) { ctx ->
            BlockchainApi.initializeBlockchain(ctx, brid, false, blockchainConfig, listOf())
            brid
        }
    }

    private fun addConfiguration(
            appConfig: AppConfig,
            blockchainConfig: Gtv,
            chainId: Long,
            height: Long
    ) {
        runStorageCommand(appConfig, chainId, true) { ctx: EContext ->
            try {
                if (!BlockchainApi.addConfiguration(ctx, height, false, blockchainConfig, allowUnknownSigners = false))
                    println("Blockchain configuration of chainId $chainId at height $height already exists")
            } catch (e: BadDataMistake) {
                if (e.type == BadDataType.MISSING_PEERINFO) {
                    throw CliException(e.message + " Please add node with command peerinfo-add or set flag --allow-unknown-signers.")
                } else {
                    throw CliException("Bad configuration format.")
                }
            }
        }
    }
}
