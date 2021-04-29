package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.config.node.FileNodeConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigProviders
import net.postchain.containers.NameService
import net.postchain.core.Infrastructure
import org.apache.commons.configuration2.ConfigurationUtils
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class DefaultContainerInitializer(val nodeConfig: NodeConfig) : ContainerInitializer {

    companion object : KLogging()

    // Just for tests
    private fun m(message: String) = "\t" + message

    override fun createContainerWorkingDir(chainId: Long): Pair<Path, Path> {
        // Creating current working dir (target)
        val containerDir = Paths.get(nodeConfig.appConfig.configDir, "containers", chainId.toString())
        val containerChainConfigsDir = containerDir.resolve("blockchains${File.separator}$chainId")
        if (containerChainConfigsDir.toFile().exists()) {
            logger.info(m("Container chain dir exists: $containerChainConfigsDir"))
        } else {
            val created = containerChainConfigsDir.toFile().mkdirs()
            logger.info(m("Container chain dir ${if (created) "has" else "hasn't"} been created: $containerChainConfigsDir"))
        }

        return containerDir to containerChainConfigsDir
    }

    override fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path) {
        // Cloning original nodeConfig
        val config = ConfigurationUtils.cloneConfiguration(nodeConfig.appConfig.config)

        // Setting up params for container node
        config.setProperty("configuration.provider.node", NodeConfigProviders.File.name.toLowerCase())
        config.setProperty("infrastructure", Infrastructure.EbftContainerSlave.get())

        val scheme = NameService.databaseSchema(nodeConfig, container.containerName)
        config.setProperty("database.schema", scheme)

        config.setProperty("containerChains.masterHost", nodeConfig.masterHost)
        config.setProperty("containerChains.masterPort", nodeConfig.masterPort)

        //TODO: works only for linux?
        if (config.containsKey("subnode.database.url")) {
            config.setProperty("database.url", config.getProperty("subnode.database.url"))
            config.clearProperty("subnode.database.url")
            //        config.setProperty("database.url", "jdbc:postgresql://172.17.0.1:5432/postchain")
        }
        // Adding peerInfos property as array/list
        val peerInfos = FileNodeConfigurationProvider.packPeerInfoCollection(nodeConfig.peerInfoMap.values)
        config.setProperty("peerinfos", peerInfos)

        // Creating a nodeConfig file
        val filename = containerDir.resolve("node-config.properties").toString()
        AppConfig.toPropertiesFile(config, filename)
        logger.info(m("Container slave node properties file has been created: $filename"))
    }

}