package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.infra.ContainerNodeConfig
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class ZfsFileSystem(private val config: ContainerNodeConfig) : FileSystem {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = rootOf(containerName)
        return if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
            root
        } else {
            val fs = "${config.zfsPoolName}/${containerName.name}"
            val quota = resourceLimits.storageMb()
            val createCommand = if (config.zfsPoolInitScript != null && File(config.zfsPoolInitScript).exists()) {
                arrayOf("/bin/sh", config.zfsPoolInitScript, fs, quota.toString())
            } else {
                if (resourceLimits.hasStorage()) {
                    arrayOf("/usr/sbin/zfs", "create", "-u", "-o", "quota=${quota}m", "-o", "reservation=50m", fs)
                } else {
                    arrayOf("/usr/sbin/zfs", "create", "-u", fs)
                }
            }
            try {
                runCommand(createCommand)?.let {
                    logger.info("Unable to create ZFS file system: $it")
                }
                runCommand(arrayOf("/usr/sbin/zfs", "mount", fs))?.let {
                    logger.warn("Unable to mount ZFS file system: $it")
                }
            } catch (e: Exception) {
                logger.error("Can't create container dir: $root", e)
            }
            if (root.toFile().exists()) {
                logger.info("Container dir has been created: $root")
                root
            } else {
                logger.error("Container dir hasn't been created: $root")
                null
            }
        }
    }

    private fun runCommand(cmd: Array<String>): String? {
        logger.debug("Executing command: ${cmd.contentToString()}")
        val process = Runtime.getRuntime().exec(cmd)
        process.waitFor(10, TimeUnit.SECONDS)
        return if (process.exitValue() != 0) {
            String(process.errorStream.readAllBytes())
        } else {
            null
        }
    }

    override fun rootOf(containerName: ContainerName): Path {
        return hostRootOf(containerName)
    }

    override fun hostRootOf(containerName: ContainerName): Path {
        return Paths.get(File.separator, config.zfsPoolName, containerName.name)
    }
}
