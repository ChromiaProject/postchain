// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import mu.KLogging
import net.postchain.cli.util.debugOption
import net.postchain.cli.util.printCommandInfo
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
        printCommandInfo()

        val chainsDir = Paths.get(configDirectory, BLOCKCHAIN_DIR).toFile()
        val nodeConfigFile = Paths.get(configDirectory, NODE_CONFIG_FILE).toString()

        CliExecution.waitDb(50, 1000, nodeConfigFile)
        val chainIds = loadChainsConfigs(chainsDir, nodeConfigFile)
        CliExecution.runNode(nodeConfigFile, chainIds.sorted(), debug)
        println("Postchain node is running")
    }

    /**
     * Loads configs of chains into DB
     * @return list of ids of found chains
     */
    private fun loadChainsConfigs(chainsDir: File, nodeConfigFile: String): List<Long> {
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

                        configs.filterKeys { it > (lastHeights[chainId] ?: -1) }
                                .toSortedMap()
                                .forEach { (height, file) ->
                                    val blockchainConfigFile = file.absolutePath
                                    if (height == 0L) {
                                        CliExecution.addBlockchain(
                                                nodeConfigFile, chainId, blockchainConfigFile)
                                    } else {
                                        CliExecution.addConfiguration(
                                                nodeConfigFile, blockchainConfigFile, chainId, height.toLong())
                                    }
                                    lastHeights[chainId] = height
                                    logger.info { "Chain (chainId: $chainId) configuration at height $height has been added" }
                                }
                    }
        }

        return chainIds
    }

}