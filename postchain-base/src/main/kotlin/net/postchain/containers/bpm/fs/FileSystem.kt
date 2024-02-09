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
        const val CONTAINER_PGDATA_PATH = "/var/lib/postgresql/data/"
        const val PGDATA_DIR = "pgdata"

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
}
