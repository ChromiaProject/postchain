package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path
import java.nio.file.Paths

class LocalFileSystem(private val containerConfig: ContainerNodeConfig) : FileSystem {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = rootOf(containerName)
        return if (root.toFile().exists()) {
            logger.info("Container dir exists: $root")
            root
        } else {
            val created = root.toFile().mkdirs()
            logger.info("Container dir ${if (created) "has" else "hasn't"} been created: $root")
            if (created) root else null
        }
    }

    override fun rootOf(containerName: ContainerName): Path {
        return Paths.get(containerConfig.masterMountDir, containerName.name)
    }

    override fun hostRootOf(containerName: ContainerName): Path {
        return Paths.get(containerConfig.hostMountDir, containerName.name)
    }

}