package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.fs.FileSystem.Type.ZFS
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path

/**
 * File system structure:
 * .
 * └── target/
 *     ├── blockchains/
 *     │   ├── 0/                              chainId
 *     │   │   ├── 0.gtv                       config0 / gtv
 *     │   │   ├── 0.xml                       config0 / xml
 *     │   │   ├── 10.gtv                      config10 / gtv
 *     │   │   ├── 10.xml                      config10 / gtv
 *     │   │   └── brid.txt                    brid
 *     │   ├── 1/                              chainId
 *     │   │   └── ...
 *     │   └── ...
 *     ├── containers/
 *     │   ├── n020CCD8A_cont0/                container
 *     │   │   ├── logs                        logs
 *     │   │   ├── env-peers.sh                this node host, port, pubkey
 *     │   │   └── node-config.properties      node config file
 *     │   ├── n020CCD8A_cont1/                container
 *     │   │   └── ...
 *     │   └── ...
 *     ├── .initialized
 *     ├── env-peers.sh                        this node host, port, pubkey
 *     ├── keys.properties                     node priv/pub key
 *     └── node-config.properties              node config file
 *
 */
interface FileSystem {

    // Filesystem type
    enum class Type {
        LOCAL, ZFS
    }

    companion object : KLogging() {

        const val ZFS_POOL_NAME = "psvol"
        const val CONTAINER_PGDATA_PATH = "/var/lib/postgresql/data/"
        const val PGDATA_DIR = "pgdata"
        const val BLOCKCHAINS_DIR = "blockchains"
        const val PEERS_FILE = "env-peers.sh"

        fun create(containerConfig: ContainerNodeConfig): FileSystem {
            return when (containerConfig.containerFilesystem) {
                ZFS.name -> ZfsFileSystem(containerConfig)
                else -> LocalFileSystem(containerConfig)
            }
        }
    }

    /**
     * Creates and returns root of container
     */
    fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path?

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
