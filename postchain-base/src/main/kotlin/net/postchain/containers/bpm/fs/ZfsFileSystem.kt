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
            try {
                val script = config.zfsPoolInitScript
                if (!File(script).exists()) {
                    logger.error("Can't find zfs init script: $script")
                    null
                } else {
                    val fs = "${config.zfsPoolName}/${containerName.name}"
                    val quota = resourceLimits.storageMb().toString()
                    val cmd = arrayOf("/bin/sh", script, fs, quota)
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

    override fun rootOf(containerName: ContainerName): Path {
        return hostRootOf(containerName)
    }

    override fun hostRootOf(containerName: ContainerName): Path {
        return Paths.get(File.separator, config.zfsPoolName, containerName.name)
    }
}
