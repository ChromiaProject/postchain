package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path
import java.nio.file.Paths

class Ext4FileSystem(private val containerConfig: ContainerNodeConfig) : FileSystem {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = rootOf(containerName)
        if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
        } else {
            if (!root.toFile().mkdirs()) {
                logger.warn("Unable to create container dir: $root")
                return null
            }
            logger.info("Container dir has been created: $root")
            containerConfig.subnodeUser?.let { subnodeUser ->
                runCommand(arrayOf("chown", subnodeUser, root.toString()))?.let {
                    logger.warn("Unable change owner of $root to $subnodeUser: $it")
                    return null
                }
            }
        }

        runCommand(arrayOf(
                "chattr",
                "+P",
                "-p", containerName.containerIID.toString(),
                root.toString()))?.let {
            logger.warn("Unable to assign project ID ${containerName.containerIID} to directory $root: $it")
            return null
        }

        val hostPgdata = hostPgdataOf(containerName)
        hostPgdata.toFile().mkdirs()
        containerConfig.subnodeUser?.let { subnodeUser ->
            runCommand(arrayOf("chown", subnodeUser, hostPgdata.toString()))?.let {
                logger.warn("Unable change owner of $hostPgdata to $subnodeUser: $it")
                return null
            }
        }

        return root
    }

    override fun rootOf(containerName: ContainerName): Path =
            Paths.get(containerConfig.masterMountDir, containerName.name)

    override fun hostRootOf(containerName: ContainerName): Path =
            Paths.get(containerConfig.hostMountDir, containerName.name)

    override fun applyLimits(containerName: ContainerName, resourceLimits: ContainerResourceLimits) {
        if (resourceLimits.hasStorage()) {
            val quota = resourceLimits.storageMb()
            logger.info("Setting storage quota: $quota MiB")
            runCommand(arrayOf(
                    "setquota",
                    "-P", containerName.containerIID.toString(),
                    "0", "${quota}M", "0", "0",
                    containerConfig.masterMountDir))?.let {
                logger.warn("Unable to set quota for project ${containerName.containerIID}: $it")
            }
        }
    }
}
