package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigProviders
import net.postchain.containers.NameService
import net.postchain.core.Infrastructure
import net.postchain.managed.ManagedNodeDataSource
import org.apache.commons.configuration2.ConfigurationUtils
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class DefaultContainerInitializer(val nodeConfig: NodeConfig) : ContainerInitializer {

    companion object : KLogging()

    // Just for tests
    private fun m(message: String) = "\t" + message

    override fun createContainerWorkingDir(process: ContainerBlockchainProcess): Pair<Path, Path> {
        // Creating current working dir (or runtime-environment (rte) dir)
        /**
         *  Windows/Docker: WSL(2) doesn't work correctly with /mnt/.
         *  We should mount /mnt/d to /d and pass paths to Docker without '/mnt' prefix.
         */
        val cwd = nodeConfig.appConfig.configDir.let {
            if (OsHelper.isWindows()) it.removePrefix("/mnt") else it
        }
        val containerCwd = Paths.get(cwd, "containers", process.chainId.toString())
        val containerChainDir = containerCwd.resolve("blockchains${File.separator}${process.chainId}")
        if (containerChainDir.toFile().exists()) {
            logger.info(m("Container chain dir exists: $containerChainDir"))
        } else {
            val created = containerChainDir.toFile().mkdirs()
            logger.info(m("Container chain dir ${if (created) "has" else "hasn't"} been created: $containerChainDir"))
        }

        return containerCwd to containerChainDir
    }

    override fun createContainerNodeConfig(chainId: Long, containerCwd: Path) {
        val config = ConfigurationUtils.cloneConfiguration(nodeConfig.appConfig.config)
        config.setProperty("configuration.provider.node", NodeConfigProviders.Manual.name.toLowerCase())
        config.setProperty("infrastructure", Infrastructure.EbftContainerSlave.get())
        config.setProperty("database.schema", NameService.databaseSchema(chainId))

        config.setProperty("containerChains.masterHost", nodeConfig.masterHost)
        config.setProperty("containerChains.masterPort", nodeConfig.masterPort)

        // Creating a nodeConfig file
        val filename = containerCwd.resolve("node-config.properties").toString()
        AppConfig.toPropertiesFile(config, filename)
        logger.info(m("Container subnode properties file has been created: $filename"))
    }

    override fun createContainerChainConfigs(dataSource: ManagedNodeDataSource, process: ContainerBlockchainProcess, chainDir: Path) {
        // Retrieving configs from dataSource/chain0
        val configs: Map<Long, ByteArray> = try {
            dataSource.getConfigurations(process.blockchainRid.data)
        } catch (e: Exception) {
            logger.error(m("Exception in dataSource.getConfigurations(): " + e.message))
            mapOf()
        }

        // Dumping all chain configs to chain dir
        // TODO: [POS-129]: Skip already dumped configs
        logger.info(m("Number of chain configs to dump: ${configs.size}"))
        configs.forEach { (height, config) ->
            val configPath = chainDir.resolve("$height.gtv")
            configPath.toFile().writeBytes(config)
            logger.info(m("Config file dumped: $configPath"))
        }
    }

}