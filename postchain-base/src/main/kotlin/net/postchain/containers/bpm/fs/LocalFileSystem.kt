package net.postchain.containers.bpm.fs

import mu.KLogging
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.command.CommandExecutor
import net.postchain.containers.infra.ContainerNodeConfig
import java.nio.file.Path
import java.nio.file.Paths

open class LocalFileSystem(protected val containerConfig: ContainerNodeConfig, protected val commandExecutor: CommandExecutor) : FileSystem {

    companion object : KLogging()

    override fun createContainerRoot(containerName: ContainerName, resourceLimits: ContainerResourceLimits): Path? {
        val root = createRoot(containerName)

        if (!createPgdata(containerName)) return null
        if (!createTmp(containerName)) return null
        if (!createPgUnixSocket(containerName)) return null

        return root
    }

    protected fun createRoot(containerName: ContainerName): Path? {
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
                commandExecutor.runCommand(arrayOf("chown", subnodeUser, root.toString()))?.let {
                    logger.warn("Unable change owner of $root to $subnodeUser: $it")
                    return null
                }
            }
        }
        return root
    }

    protected fun createPgdata(containerName: ContainerName): Boolean {
        return createDirectory(hostPgdataOf(containerName))
    }

    protected fun createTmp(containerName: ContainerName): Boolean {
        return createDirectory(hostTmpOf(containerName))
    }

    protected fun createPgUnixSocket(containerName: ContainerName): Boolean {
        return createDirectory(hostPgUnixSocketOf(containerName))
    }

    private fun createDirectory(directory: Path): Boolean {
        directory.toFile().mkdirs()
        containerConfig.subnodeUser?.let { subnodeUser ->
            commandExecutor.runCommand(arrayOf("chown", subnodeUser, directory.toString()))?.let {
                logger.warn("Unable change owner of $directory to $subnodeUser: $it")
                return false
            }
        }
        return true
    }

    override fun applyLimits(containerName: ContainerName, resourceLimits: ContainerResourceLimits) {}
    override fun getCurrentLimitsInfo(containerName: ContainerName, resourceLimits: ContainerResourceLimits): ResourceLimitsInfo? = null

    override fun rootOf(containerName: ContainerName): Path =
            Paths.get(containerConfig.masterMountDir, containerName.dockerContainer)

    override fun hostRootOf(containerName: ContainerName): Path =
            Paths.get(containerConfig.hostMountDir, containerName.dockerContainer)
}
