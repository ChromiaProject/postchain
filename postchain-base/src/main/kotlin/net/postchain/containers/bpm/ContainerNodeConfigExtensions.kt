package net.postchain.containers.bpm

import net.postchain.config.node.NodeConfig

const val CONTAINER_ZFS_INIT_SCRIPT = "container-zfs-init-script.sh"
/**
 * Container chains
 */
val NodeConfig.containerImage: String
    get() = appConfig.config.getString("containerChains.dockerImage", "chromaway/postchain-subnode:latest")
val NodeConfig.subnodeRestApiPort: Int
    get() = appConfig.config.getInt("containerChains.api.port", 7740)

// Used by subnode to connect to master for inter-node communication.
val NodeConfig.masterHost: String
    get() = appConfig.config.getString("containerChains.masterHost", "localhost")

val NodeConfig.masterPort: Int
    get() = appConfig.config.getInt("containerChains.masterPort", 9860)

// Used by master for restAPI communication with subnode
val NodeConfig.subnodeHost: String
    get() = appConfig.config.getString("containerChains.subnodeHost", "localhost")

val NodeConfig.containerSendConnectedPeersPeriod: Long
    get() = appConfig.config.getLong("container.send-connected-peers-period", 60_000L)

val NodeConfig.runningContainersAtStartRegexp: String
    get() = appConfig.config.getString("container.healthcheck.runningContainersAtStartRegexp", "")
val NodeConfig.runningContainersCheckPeriod: Int // In number of blocks of chain0, set 0 to disable a check
    get() = appConfig.config.getInt("container.healthcheck.runningContainersCheckPeriod", 0)

// Container FileSystem
val NodeConfig.containerFilesystem: String
    get() = appConfig.config.getString("container.filesystem", FileSystem.Type.LOCAL.name).toUpperCase() // LOCAL | ZFS
val NodeConfig.containerZfsPool: String
    get() = appConfig.config.getString("container.zfs.pool-name", FileSystem.ZFS_POOL_NAME)
val NodeConfig.containerZfsPoolInitScript: String
    get() = appConfig.config.getString("container.zfs.pool-init-script", CONTAINER_ZFS_INIT_SCRIPT)
val NodeConfig.containerBindPgdataVolume: Boolean
    get() = appConfig.config.getBoolean("container.bind-pgdata-volume", true)
