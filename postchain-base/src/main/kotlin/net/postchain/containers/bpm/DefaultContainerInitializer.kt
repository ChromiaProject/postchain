package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders
import net.postchain.containers.bpm.FileSystem.Companion.BLOCKCHAINS_DIR
import net.postchain.containers.bpm.FileSystem.Companion.NODE_CONFIG_FILE
import net.postchain.containers.bpm.FileSystem.Companion.PEERS_FILE
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_SUBNODE_DATABASE_URL
import net.postchain.containers.infra.ContainerNodeConfig.Companion.fullKey
import net.postchain.core.Infrastructure
import java.io.File
import java.nio.file.Path

internal class DefaultContainerInitializer(private val appConfig: AppConfig, private val containerConfig: ContainerNodeConfig) : ContainerInitializer {

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
        val config = appConfig.cloneConfiguration()

        // Setting up params for container node
        config.setProperty("configuration.provider.node", NodeConfigProviders.Manual.name.lowercase())
        config.setProperty("infrastructure", Infrastructure.EbftContainerSub.get())

        // DB
        if (config.containsKey(fullKey(KEY_SUBNODE_DATABASE_URL))) {
            config.setProperty("database.url", config.getProperty(fullKey(KEY_SUBNODE_DATABASE_URL)))
        }
        val scheme = databaseSchema(container.containerName)
        config.setProperty("database.schema", scheme)

        /**
         * If restApiPort > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * If -1, no API communication => subnodeRestApiPort=-1
         */
        if (config.getInt("api.port", 7740) > -1) {
            config.setProperty("api.port", containerConfig.subnodeRestApiPort)
        }

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
        return fs.rootOf(chain.containerName)
            .resolve(BLOCKCHAINS_DIR)
            .resolve(chain.chainId.toString())
    }

    private fun databaseSchema(containerName: ContainerName): String {
        return "${appConfig.databaseSchema}_${containerName}"
    }
}
