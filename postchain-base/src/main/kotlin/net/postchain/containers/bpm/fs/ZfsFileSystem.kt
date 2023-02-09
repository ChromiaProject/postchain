package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.infra.ContainerNodeConfig
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class ZfsFileSystem(private val containerConfig: ContainerNodeConfig) : FileSystem {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = rootOf(containerName)
        if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
        } else {
            val fs = "${containerConfig.zfsPoolName}/${containerName.name}"
            val quota = resourceLimits.storageMb()

            if (runCommand(arrayOf("zfs", "get", "all", fs)) == null) {
                logger.info("ZFS volume exists: $fs")
            } else {
                logger.info("Creating ZFS volume: $fs")
                val createCommand = if (containerConfig.zfsPoolInitScript != null && File(containerConfig.zfsPoolInitScript).exists()) {
                    arrayOf("/bin/sh", containerConfig.zfsPoolInitScript, fs, quota.toString())
                } else {
                    if (resourceLimits.hasStorage()) {
                        arrayOf("zfs", "create", "-u", "-o", "quota=${quota}m", "-o", "reservation=50m", fs)
                    } else {
                        arrayOf("zfs", "create", "-u", fs)
                    }
                }
                runCommand(createCommand)?.let {
                    logger.warn("Unable to create ZFS file system: $it")
                }
            }

            runCommand(arrayOf("zfs", "mount", fs))?.let {
                logger.warn("Unable to mount ZFS file system: $it")
            }
            if (root.toFile().exists()) {
                logger.info("Container dir has been created: $root")
            } else {
                logger.error("Container dir hasn't been created: $root")
                return null
            }
        }

        val hostPgdata = hostPgdataOf(containerName)
        hostPgdata.toFile().mkdirs()

        return root
    }

    override fun rootOf(containerName: ContainerName): Path = hostRootOf(containerName)

    override fun hostRootOf(containerName: ContainerName): Path =
            Paths.get(File.separator, containerConfig.zfsPoolName, containerName.name)
}
