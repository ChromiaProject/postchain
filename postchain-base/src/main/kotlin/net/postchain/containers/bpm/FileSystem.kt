package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.FileSystem.Type.ZFS
import net.postchain.containers.infra.ContainerNodeConfig
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Directory structure:
 *      ./target
 *          /blockchains
 *              /0                                  chainId
 *                  0.gtv                           config0
 *                  0.xml                           config0
 *                  1.gtv                           config1
 *                  1.xml                           config1
 *                  brid.txt
 *              /1                                  chainId
 *                  /...
 *              /...
 *          /containers
 *              /n020CCD8A_cont0                    container name
 *                  /blockchains                    blockchains of this container
 *                      /100
 *                          /0.gtv                  config0
 *                          /0.xml                  config0
 *                          /brid.txt
 *                      /101
 *                          /...
 *                      /...
 *                  /logs                           logs
 *                  /env-peers.sh                   this node host, port, pub-key
 *                  /node-config.properties         node config file
 *              /n020CCD8A_cont1                    container name
 *                  /...
 *              /...
 *          /.initialized
 *          /env-peers.sh                           this node host, port, pub-key
 *          /keys.properties                        node priv/pub key
 *          /node-config.properties                 node config file
 *
 */
class FileSystem(private val appConfig: AppConfig, private val containerNodeConfig: ContainerNodeConfig) {

    // Filesystem type
    enum class Type {
        LOCAL, ZFS
    }

    companion object : KLogging() {
        const val ZFS_POOL_NAME = "psvol"
        const val CONTAINER_TARGET_PATH = "/opt/chromaway/postchain/target"
        const val CONTAINER_PGDATA_PATH = "/var/lib/postgresql/data/"
        const val PGDATA_DIR = "pgdata"
        const val CONTAINERS_DIR = "containers"
        const val BLOCKCHAINS_DIR = "blockchains"
        const val NODE_CONFIG_FILE = "node-config.properties"
        const val PEERS_FILE = "env-peers.sh"
    }

    /**
     * Creates and returns root of container
     */
    fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        return if (containerNodeConfig.containerFilesystem == ZFS.name) {
            createZfsContainerRoot(containerName, resourceLimits)
        } else { // LOCAL
            createLocalContainerRoot(containerName)
        }
    }

    fun hostRootOf(containerName: ContainerName): Path {
        return containerRoot(containerName)
    }

    fun hostPgdataOf(containerName: ContainerName): Path {
        return containerRoot(containerName).resolve(PGDATA_DIR)
    }

    fun containerTargetPath() = CONTAINER_TARGET_PATH

    fun containerPgdataPath() = CONTAINER_PGDATA_PATH

    private fun containerRoot(containerName: ContainerName): Path {
        return if (containerNodeConfig.containerFilesystem == ZFS.name) {
            zfsRootOf(containerName)
        } else { // LOCAL
            localRootOf(containerName)
        }
    }

    private fun zfsRootOf(containerName: ContainerName): Path {
        return Paths.get(File.separator, containerNodeConfig.containerZfsPool, containerName.name)
    }

    private fun localRootOf(containerName: ContainerName): Path {
        return Paths.get(appConfig.configDir, CONTAINERS_DIR, containerName.name)
    }

    private fun createZfsContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = zfsRootOf(containerName)
        return if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
            root
        } else {
            try {
                val script = "./${containerNodeConfig.containerZfsPoolInitScript}"
                if (!File(script).exists()) {
                    logger.error("Can't find zfs init script: $script")
                    null
                } else {
                    val fs = "${containerNodeConfig.containerZfsPool}/${containerName.name}"
                    val quota = resourceLimits.storage.toString()
                    val cmd = arrayOf(script, fs, quota)
                    Runtime.getRuntime().exec(cmd).waitFor(10, TimeUnit.SECONDS)
                    if (root.toFile().exists()) {
                        logger.info("Container dir has been created: $root")
                        root
                    } else {
                        logger.error("Container dir hasn't been created: $root")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Can't create container dir: $root", e)
                null
            }
        }
    }

    private fun createLocalContainerRoot(containerName: ContainerName): Path? {
        val root = localRootOf(containerName)
        return if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
            root
        } else {
            val created = root.toFile().mkdirs()
            logger.info("Container dir ${if (created) "has" else "hasn't"} been created: $root")
            if (created) root else null
        }
    }
}