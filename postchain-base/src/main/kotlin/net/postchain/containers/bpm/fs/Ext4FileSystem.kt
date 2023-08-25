package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path

class Ext4FileSystem(containerConfig: ContainerNodeConfig) : LocalFileSystem(containerConfig) {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root =  createRoot(containerName)

        runCommand(arrayOf(
                "chattr",
                "+P",
                "-p", containerName.containerIID.toString(),
                root.toString()))?.let {
            logger.warn("Unable to assign project ID ${containerName.containerIID} to directory $root: $it")
            return null
        }

        if (!createPgdata(containerName)) return null

        return root
    }

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

    override fun getCurrentLimitsInfo(containerName: ContainerName, resourceLimits: ContainerResourceLimits): ResourceLimitsInfo? {
        val projectName = containerName.containerIID.toString()
        if (resourceLimits.hasStorage()) {
            /*
            repquota command will return something like this:
                Project,SpaceStatus,FileStatus,SpaceUsed,SpaceSoftLimit,SpaceHardLimit,SpaceGrace,FileUsed,FileSoftLimit,FileHardLimit,FileGrace
                #0,ok,ok,1M,0M,0M,,1k,0k,0k,
                #1,ok,ok,51M,0M,16384M,,2k,0k,0k,
             */
            runCommandWithOutput(arrayOf(
                    "repquota",
                    "-P",
                    "--human-readable=m,k",
                    "-O", "csv",
                    containerConfig.masterMountDir)).let {
                if (it.exitValue != 0) {
                    logger.warn("Unable to get quota report for project ${containerName.containerIID}: ${it.output}")
                } else {
                    logger.debug { "Result from quota report for project ${containerName.containerIID}: ${it.output}" }
                    for (line in it.output) {
                        if (line.startsWith("#")) {
                            val columns = line.split(",")
                            val project = columns[0].removePrefix("#")
                            if (projectName == project) {
                                val spaceUsed = columns[3].removeSuffix("M").toLong()
                                val spaceHardLimit = columns[5].removeSuffix("M").toLong()
                                return ResourceLimitsInfo(spaceUsed, spaceHardLimit)
                            }
                        }
                    }
                }
            }
        }
        return null
    }
}
