package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.bpm.fs.FileSystem.Companion.NODE_CONFIG_FILE
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_SUBNODE_DATABASE_URL
import net.postchain.containers.infra.ContainerNodeConfig.Companion.fullKey
import net.postchain.core.Infrastructure
import java.nio.file.Path

internal class DefaultContainerInitializer(private val appConfig: AppConfig, private val containerConfig: ContainerNodeConfig) : ContainerInitializer {

    companion object : KLogging()

    override fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path? {
        val dir = fs.createContainerRoot(container.containerName, container.resourceLimits)
        if (dir != null) {
            createContainerNodeConfig(container, dir)
        }
        return dir
    }

    private fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path) {
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
        if (config.getInt("api.port", RestApiConfig.DEFAULT_REST_API_PORT) > -1) {
            config.setProperty("api.port", containerConfig.subnodeRestApiPort)
        }

        // Creating a nodeConfig file
        val filename = containerDir.resolve(NODE_CONFIG_FILE).toString()
        AppConfig.toPropertiesFile(config, filename)
        logger.info("Container subnode properties file has been created: $filename")
    }

    private fun databaseSchema(containerName: ContainerName): String {
        return "${appConfig.databaseSchema}_${containerName.directoryContainer}"
    }
}
