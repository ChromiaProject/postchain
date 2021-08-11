package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.app.AppConfig
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

    override fun createContainerChainWorkingDir(chainId: Long, containerName: String): Pair<Path, Path> {
        // Creating current working dir (target)
        val containerDir = Paths.get(nodeConfig.appConfig.configDir, "containers", containerName)
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
        config.setProperty("configuration.provider.node", NodeConfigProviders.Manual.name.toLowerCase())
        config.setProperty("infrastructure", Infrastructure.EbftContainerSlave.get())

        val scheme = NameService.databaseSchema(nodeConfig, container.nodeContainerName)
        config.setProperty("database.schema", scheme)

        // Heartbeat and RemoteConfig
        // TODO: [POS-164]: Heartbeat and RemoteConfig
        // val defaultNodeConfig = NodeConfig(AppConfig(<empty-apache-config>))
        config.setProperty("heartbeat.enabled", false)
        config.setProperty("remote_config.enabled", false)

        config.setProperty("containerChains.masterHost", nodeConfig.masterHost)
        config.setProperty("containerChains.masterPort", nodeConfig.masterPort)

        /**
         * If nodeConfig.restApiPost > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * If -1, no API communication => subnodeRestApiPort=restApiPost
         */
        if (nodeConfig.restApiPort > -1) config.setProperty("api.port", nodeConfig.subnodeRestApiPort)

        //TODO: works only for linux?
        if (config.containsKey("subnode.database.url")) {
            config.setProperty("database.url", config.getProperty("subnode.database.url"))
            config.clearProperty("subnode.database.url")
            //        config.setProperty("database.url", "jdbc:postgresql://172.17.0.1:5432/postchain")
        }

        // Creating a nodeConfig file
        val filename = containerDir.resolve("node-config.properties").toString()
        AppConfig.toPropertiesFile(config, filename)
        logger.info(m("Container subnode properties file has been created: $filename"))
    }

    override fun createPeersConfig(container: PostchainContainer, containerDir: Path) {
        val peers = """
            export NODE_HOST=127.0.0.1
            export NODE_PORT=${NodeConfig.DEFAULT_PORT}
            export NODE_PUBKEY=${nodeConfig.pubKey}
            
        """.trimIndent()

        val filename = containerDir.resolve("env-peers.sh").toString()
        File(filename).writeText(peers)
        logger.info(m("Container subnode peers file has been created: $filename"))
    }

    override fun killContainerChainWorkingDir(chainId: Long, containerName: String) {
        // Creating current working dir (target)
        val containerDir = Paths.get(nodeConfig.appConfig.configDir, "containers", containerName)
        val containerChainConfigsDir = containerDir.resolve("blockchains${File.separator}$chainId")
        if (containerChainConfigsDir.toFile().deleteRecursively()) {
            logger.info(m("Container chain dir has been deleted: $containerChainConfigsDir"))
        } else {
            logger.info(m("Container chain dir hasn't been deleted properly: $containerChainConfigsDir"))
        }
    }

}