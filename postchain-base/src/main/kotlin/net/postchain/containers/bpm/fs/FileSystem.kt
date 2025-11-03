package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.command.DefaultCommandExecutor
import net.postchain.containers.bpm.fs.FileSystem.Type.EXT4
import net.postchain.containers.bpm.fs.FileSystem.Type.ZFS
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path

/**
 * File system structure:
 * .
 * └── target/
 *     └── logs/
 */
interface FileSystem {

    // Filesystem type
    enum class Type {
        LOCAL, ZFS, EXT4
    }

    companion object : KLogging() {

        const val ZFS_POOL_NAME = "psvol"
        const val CONTAINER_LOG4J_PATH = "/opt/chromaway/postchain/log4j2.yml"
        const val CONTAINER_TARGET_PATH = "/opt/chromaway/postchain/target"
        const val CONTAINER_LIBS_PATH = "/opt/chromaway/postchain/libs"
        const val CONTAINER_PGDATA_PATH = "/var/lib/postgresql/data/"
        const val CONTAINER_PG_UNIX_SOCKET_PATH = "/var/run/postgresql/"
        const val CONTAINER_TMP_PATH = "/tmp/"
        const val PGDATA_DIR = "pgdata"
        const val TMP_DIR = "tmp"
        const val PGRUN_DIR = "pgrun"

        fun create(containerConfig: ContainerNodeConfig): FileSystem {
            return when (containerConfig.containerFilesystem) {
                ZFS.name -> ZfsFileSystem(containerConfig, DefaultCommandExecutor)
                EXT4.name -> Ext4FileSystem(containerConfig, DefaultCommandExecutor)
                else -> LocalFileSystem(containerConfig, DefaultCommandExecutor)
            }
        }
    }

    /**
     * Creates and returns root of container
     */
    fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path?

    fun applyLimits(containerName: ContainerName, resourceLimits: ContainerResourceLimits)
    fun getCurrentLimitsInfo(containerName: ContainerName, resourceLimits: ContainerResourceLimits): ResourceLimitsInfo?

    /**
     * Returns root of container in the master (container) filesystem
     */
    fun rootOf(containerName: ContainerName): Path

    /**
     * Returns root of container in the host filesystem
     */
    fun hostRootOf(containerName: ContainerName): Path

    /**
     * Returns pgdata of container in the host filesystem
     */
    fun hostPgdataOf(containerName: ContainerName): Path {
        return hostRootOf(containerName).resolve(PGDATA_DIR)
    }

    /**
     * Returns tmp of container in the host filesystem
     */
    fun hostTmpOf(containerName: ContainerName): Path {
        return hostRootOf(containerName).resolve(TMP_DIR)
    }

    /**
     * Returns Postgresql unix socket path of container in the host filesystem
     */
    fun hostPgUnixSocketOf(containerName: ContainerName): Path {
        return hostRootOf(containerName).resolve(PGRUN_DIR)
    }

    /**
     * Remove recursively a root.
     */
    fun removeRoot(containerName: ContainerName) {

        logger.info("Removing $containerName container storage located at: ${rootOf(containerName)}")

        rootOf(containerName).toFile().deleteRecursively()
    }

    /**
     * Creates (if not exists) and returns extensions directory
     */
    fun extensionsDir(): Path

    fun supportsQuotas() = false
}
