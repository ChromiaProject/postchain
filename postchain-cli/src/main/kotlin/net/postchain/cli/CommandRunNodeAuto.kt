// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import mu.KLogging
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.CliExecution.addConfiguration
import net.postchain.cli.CliExecution.findBlockchainRid
import net.postchain.cli.util.debugOption
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import net.postchain.gtv.GtvFileReader
import java.io.File
import java.nio.file.Paths

class CommandRunNodeAuto : CliktCommand(name = "run-node-auto", help = "Run Node Auto") {

    companion object : KLogging()

    // TODO Olle No longer needed to have a brid.txt (Blockchan RID) file, should remove it from tests.
    //./postchain-devtools/src/test/resources/net/postchain/devtools/cli/brid.txt
    //./postchain-base/src/main/jib/config/blockchains/1/brid.txt

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

    private val NODE_CONFIG_FILE = "node-config.properties"
    private val BLOCKCHAIN_DIR = "blockchains"

    private val lastHeights = mutableMapOf<Long, Long>() // { chainId -> height }

    override fun run() {
        val chainsDir = Paths.get(configDirectory, BLOCKCHAIN_DIR).toFile()
        val nodeConfigFile = Paths.get(configDirectory, NODE_CONFIG_FILE).toFile()
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)

        CliExecution.waitDb(50, 1000, appConfig)
        val chainIds = loadChainsConfigs(chainsDir, appConfig)
        CliExecution.runNode(appConfig, chainIds.sorted(), debug)
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
                    ?.forEach { dir ->
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
                            return@forEach
                        }

                        lastHeights[chainId] = if (chainExists) {
                            runStorageCommand(appConfig, chainId) { ctx: EContext ->
                                BlockchainApi.getLastBlockHeight(ctx)
                            }
                        } else -1L

                        run {
                            configs.filterKeys { it > (lastHeights[chainId] ?: -1) }
                                    .toSortedMap()
                                    .forEach { (height, blockchainConfigFile) ->
                                        val gtv = try {
                                            GtvFileReader.readFile(blockchainConfigFile)
                                        } catch (e: Exception) {
                                            println("Configuration for chain $chainId can not be loaded at height $height " +
                                                    "from the file ${blockchainConfigFile.path}, an error occurred: ${e.message}")
                                            if (height == 0L) return@run
                                            else return@forEach
                                        }

                                        if (height == 0L) {
                                            try {
                                                CliExecution.addBlockchain(appConfig, chainId, gtv)
                                            } catch (e: CliException) {
                                                println(e.message)
                                                return@run
                                            } catch (e: Exception) {
                                                println("Can't add configuration: ${e.message}")
                                                return@run
                                            }

                                        } else {
                                            try {
                                                addConfiguration(appConfig, gtv, chainId, height.toLong(), AlreadyExistMode.IGNORE, false)
                                                logger.info { "Chain (chainId: $chainId) configuration at height $height has been added" }
                                                println("Configuration has been added successfully")
                                            } catch (e: CliException) {
                                                println(e.message)
                                            } catch (e: Exception) {
                                                println("Can't add configuration: ${e.message}")
                                            }
                                        }
                                    }
                        }

                    }
        }
        return chainIds
    }
}
