package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigProviders
import net.postchain.containers.bpm.FileSystem.Companion.BLOCKCHAINS_DIR
import net.postchain.containers.bpm.FileSystem.Companion.NODE_CONFIG_FILE
import net.postchain.containers.bpm.FileSystem.Companion.PEERS_FILE
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Infrastructure
import net.postchain.ebft.heartbeat.HeartbeatConfig
import org.apache.commons.configuration2.ConfigurationUtils
import java.io.File
import java.nio.file.Path

internal class DefaultContainerInitializer(val appConfig: AppConfig, private val heartbeatConfig: HeartbeatConfig, private val containerConfig: ContainerNodeConfig) : ContainerInitializer {

    companion object : KLogging()

    override fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path? {
        val dir = fs.createContainerRoot(container.containerName, container.resourceLimits)
        if (dir != null) {
            createContainerNodeConfig(container, dir)
            createPeersConfig(container, dir)
        }

        return dir
    }

    override fun initContainerChainWorkingDir(fs: FileSystem, chain: Chain): Path? {
        // Creating current working dir (target)
        val dir = getContainerChainDir(fs, chain)
        val exists = if (dir.toFile().exists()) {
            logger.info("Container chain dir exists: $dir")
            true
        } else {
            val created = dir.toFile().mkdirs()
            logger.info("Container chain dir ${if (created) "has" else "hasn't"} been created: $dir")
            created
        }

        return if (exists) dir else null
    }

    override fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path) {
        // Cloning original nodeConfig
        val config = ConfigurationUtils.cloneConfiguration(appConfig.config)

        // Setting up params for container node
        config.setProperty("configuration.provider.node", NodeConfigProviders.Manual.name.toLowerCase())
        config.setProperty("infrastructure", Infrastructure.EbftContainerSub.get())

        // DB
        if (config.containsKey("subnode.database.url")) {
            config.setProperty("database.url", config.getProperty("subnode.database.url"))
            config.clearProperty("subnode.database.url")
            //        config.setProperty("database.url", "jdbc:postgresql://172.17.0.1:5432/postchain")
        }
        val scheme = databaseSchema(container.containerName)
        config.setProperty("database.schema", scheme)

        // Heartbeat and RemoteConfig
        // TODO: [POS-164]: Heartbeat and RemoteConfig
        // val defaultNodeConfig = NodeConfig(AppConfig(<empty-apache-config>))
        config.setProperty("heartbeat.enabled", heartbeatConfig.heartbeatEnabled)
        config.setProperty("remote_config.enabled", heartbeatConfig.remoteConfigEnabled)

        config.setProperty("containerChains.masterHost", containerConfig.masterHost)
        config.setProperty("containerChains.masterPort", containerConfig.masterPort)

        /**
         * If nodeConfig.restApiPost > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * If -1, no API communication => subnodeRestApiPort=restApiPost
         */
        if (config.getInt("restApiPort") > -1) config.setProperty("api.port", containerConfig.subnodeRestApiPort)

        // Creating a nodeConfig file
        val filename = containerDir.resolve(NODE_CONFIG_FILE).toString()
        AppConfig.toPropertiesFile(config, filename)
        logger.info("Container subnode properties file has been created: $filename")
    }

    override fun createPeersConfig(container: PostchainContainer, containerDir: Path) {
        val peers = """
            export NODE_HOST=127.0.0.1
            export NODE_PORT=${ContainerNodeConfig.DEFAULT_PORT}
            export NODE_PUBKEY=${appConfig.pubKey}
            
        """.trimIndent()

        val filename = containerDir.resolve(PEERS_FILE).toString()
        File(filename).writeText(peers)
        logger.info("Container subnode peers file has been created: $filename")
    }

    override fun removeContainerChainDir(fs: FileSystem, chain: Chain): Boolean {
        // Deleting chain working dir
        val dir = getContainerChainDir(fs, chain)
        val res = dir.toFile().deleteRecursively()
        if (res) {
            logger.info("Container chain dir has been deleted: $dir")
        } else {
            logger.info("Container chain dir hasn't been deleted properly: $dir")
        }
        return res
    }

    private fun getContainerChainDir(fs: FileSystem, chain: Chain): Path {
        return fs.hostRootOf(chain.containerName)
                .resolve(BLOCKCHAINS_DIR)
                .resolve(chain.chainId.toString())
    }

    private fun databaseSchema(containerName: ContainerName): String {
        return "${appConfig.databaseSchema}_${containerName}"
    }
}
