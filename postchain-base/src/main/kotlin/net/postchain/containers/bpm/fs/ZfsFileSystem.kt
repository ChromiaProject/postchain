package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.command.CommandExecutor
import net.postchain.containers.infra.ContainerNodeConfig
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class ZfsFileSystem(private val containerConfig: ContainerNodeConfig, private val commandExecutor: CommandExecutor) : FileSystem {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = rootOf(containerName)
        if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
        } else {
            val fs = getFs(containerName)
            val quotaBytes = getQuotaBytes(resourceLimits)

            if (commandExecutor.runCommand(arrayOf("zfs", "get", "all", fs)) == null) {
                logger.info("ZFS volume exists: $fs")
            } else {
                logger.info("Creating ZFS volume: $fs")
                val createCommand = if (containerConfig.zfsPoolInitScript != null && File(containerConfig.zfsPoolInitScript).exists()) {
                    arrayOf("/bin/sh", containerConfig.zfsPoolInitScript, fs, quotaBytes.toString())
                } else {
                    arrayOf("zfs", "create", "-u", fs)
                }
                commandExecutor.runCommand(createCommand)?.let {
                    logger.warn("Unable to create ZFS file system: $it")
                }
            }

            commandExecutor.runCommand(arrayOf("zfs", "mount", fs))?.let {
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

    override fun applyLimits(containerName: ContainerName, resourceLimits: ContainerResourceLimits) {
        if (resourceLimits.hasStorage()) {
            commandExecutor.runCommand(arrayOf("zfs", "set", "quota=${getQuotaBytes(resourceLimits)}", "reservation=${getQuotaBytes(resourceLimits)}", getFs(containerName)))?.let {
                logger.warn("Unable to set ZFS quota: $it")
            }
        }
    }

    override fun getCurrentLimitsInfo(containerName: ContainerName, resourceLimits: ContainerResourceLimits): ResourceLimitsInfo? {
        if (resourceLimits.hasStorage()) {
            /*
            Running:
                $ sudo zfs get used,quota -Hp -o property,value /pool1/0350fe40-cities-1
            will return something like this:
                used	16163180032
                quota	17179869184
            where headers are hidden, but they are:
                PROPERTY  VALUE
             */
            val poolFilesystem = getFs(containerName)
            commandExecutor.runCommandWithOutput(arrayOf(
                    "zfs",
                    "get",
                    "used,quota",
                    "-Hp",
                    "-o",
                    "property,value",
                    poolFilesystem)).let {
                if (it.exitValue != 0) {
                    logger.warn("Unable to get used and quota values for fs $poolFilesystem: ${it.systemOut + it.systemErr}")
                } else {
                    logger.debug { "Result from get used and quota values for fs $poolFilesystem: ${it.systemOut}" }
                    var spaceUsed: Long? = null
                    var spaceHardLimit: Long? = null
                    for (line in it.systemOut) {
                        val columns = line.split("\t")
                        if (columns[0] == "used") {
                            spaceUsed = b2MiB(columns[1].toLong())
                        }
                        if (columns[0] == "quota") {
                            spaceHardLimit = b2MiB(columns[1].toLong())
                        }
                    }
                    if (spaceUsed != null && spaceHardLimit != null) {
                        return ResourceLimitsInfo(spaceUsed, spaceHardLimit)
                    }
                    logger.warn("Failed to parse used and quota values for fs $poolFilesystem from: ${it.systemOut}")
                }
            }
        }
        return null
    }

    private fun getFs(containerName: ContainerName) = "${containerConfig.zfsPoolName}/${containerName.name}"

    private fun getQuotaBytes(resourceLimits: ContainerResourceLimits) = miB2B(resourceLimits.storageMb())

    private fun miB2B(value: Long) = value * 1024 * 1024

    private fun b2MiB(value: Long) = value / 1024 / 1024

    override fun rootOf(containerName: ContainerName): Path = hostRootOf(containerName)

    override fun hostRootOf(containerName: ContainerName): Path =
            Paths.get(File.separator, containerConfig.zfsPoolName, containerName.name)
}
