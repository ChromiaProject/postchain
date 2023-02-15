package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path
import java.nio.file.Paths

class LocalFileSystem(private val containerConfig: ContainerNodeConfig) : FileSystem {

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
        }

        val hostPgdata = hostPgdataOf(containerName)
        hostPgdata.toFile().mkdirs()

        return root
    }

    override fun applyLimits(containerName: ContainerName, updates: ContainerResourceLimits) {}

    override fun rootOf(containerName: ContainerName): Path =
            Paths.get(containerConfig.masterMountDir, containerName.name)

    override fun hostRootOf(containerName: ContainerName): Path =
            Paths.get(containerConfig.hostMountDir, containerName.name)
}
