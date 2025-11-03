package net.postchain.containers.bpm

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.docker.DockerTools.asyncExecAwaitMultiResponse
import net.postchain.containers.bpm.docker.DockerTools.asyncExecAwaitSingleResponse
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.docker.DockerTools.listSubContainersCmd
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvDictionary
import net.postchain.metrics.SubContainerResourceMetrics
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

open class ContainerHandler(
        private val dockerClient: DockerClient,
        private val appConfig: AppConfig,
        private val fileSystem: FileSystem,
        private val subContainerResourceMetrics: MutableMap<String, SubContainerResourceMetrics> = mutableMapOf(),
) {
    companion object : KLogging()

    protected val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)

    fun pullImage(imageSpec: String) {
        logger.info("Pulling image $imageSpec...")
        dockerClient.pullImageCmd(imageSpec).asyncExecAwaitMultiResponse(onNext = {}, onError = {
            logger.warn("Unable to pull image $imageSpec: ${it?.message}")
        })
    }

    fun resolveJarExtensions(jarExtensions: Set<ContainerJarExtensionInfo>, rawJarFileContentFetcher: (String) -> ByteArray?): List<Path> {
        val resolvedExtensions = mutableListOf<Path>()
        val extensionsDir = fileSystem.extensionsDir()
        for (jarExtension in jarExtensions) {
            val hashFile = extensionsDir.resolve("${jarExtension.name}.sha256")
            val jarFile = extensionsDir.resolve("${jarExtension.name}.jar")
            val currentHash = if (hashFile.exists()) hashFile.toFile().readBytes() else null

            if (!currentHash.contentEquals(jarExtension.hash)) {
                logger.info("Writing JAR extension ${jarExtension.name} to disk...")

                if (!jarFile.exists()) jarFile.createFile()
                val rawJarFile = rawJarFileContentFetcher(jarExtension.name)
                        ?: throw UserMistake("Unable to fetch JAR file ${jarExtension.name}")
                val loadedFileHash = sha256Digest(rawJarFile)
                if (!loadedFileHash.contentEquals(jarExtension.hash)) {
                    throw UserMistake("Loaded JAR file for extension ${jarExtension.name} hash mismatched, expected ${jarExtension.hash.toHex()}, actual ${loadedFileHash.toHex()}")
                }
                jarFile.writeBytes(rawJarFile)

                if (currentHash == null) hashFile.createFile()
                hashFile.toFile().writeBytes(jarExtension.hash)
            }

            resolvedExtensions.add(jarFile)
        }
        return resolvedExtensions
    }

    fun createDockerContainer(containerName: ContainerName, resourceLimits: ContainerResourceLimits,
                              readOnly: Boolean, image: String, directoryContainerConfiguration: GtvDictionary,
                              jarExtensions: List<Path>): String {
        val createContainerCmd = dockerClient.createContainerCmd(image)
        ContainerConfigFactory.setConfig(createContainerCmd, fileSystem, appConfig, containerNodeConfig,
                containerName, resourceLimits, readOnly, directoryContainerConfiguration, jarExtensions)
        return createContainerCmd.exec().id!!
    }

    fun startContainer(psContainer: PostchainContainer) {
        dockerClient.startContainerCmd(psContainer.containerId!!).exec()
        registerSubContainerResourceMetrics(psContainer)
    }

    fun stopContainer(psContainer: PostchainContainer) {
        unregisterSubContainerResourceMetrics(psContainer.containerName.dockerContainer)
        dockerClient.stopContainerCmd(psContainer.containerId!!).withTimeout(10).exec()
    }

    fun removeContainer(psContainer: PostchainContainer) {
        dockerClient.removeContainerCmd(psContainer.containerName.dockerContainer).exec()
    }

    fun findContainer(containerId: String): Container? {
        val all = dockerClient.listSubContainersCmd(containerNodeConfig)
                .exec()
        return all.firstOrNull { it.hasName(containerId) }
    }

    /**
     * Tries to find host port mappings for [containerPorts] given [containerId].
     */
    fun findHostPorts(containerId: String, containerPorts: List<Int>): Map<Int, Int> {
        val info = dockerClient.inspectContainerCmd(containerId).exec()
        return containerPorts.associateWith {
            info.hostPortFor(it) ?: throw ProgrammerMistake("Container has no mapped port for $it")
        }
    }

    private fun InspectContainerResponse.hostPortFor(port: Int) = networkSettings?.ports?.bindings?.get(ExposedPort(port))
            ?.firstOrNull()?.hostPortSpec?.toInt()

    private fun registerSubContainerResourceMetrics(psContainer: PostchainContainer) {
        if (appConfig.subContainerResourceUsageMetricIntervalMs > 0) {
            val dockerContainerName = psContainer.containerName.dockerContainer
            subContainerResourceMetrics.computeIfPresent(dockerContainerName) { _, _ ->
                unregisterSubContainerResourceMetrics(dockerContainerName)
                null
            }
            subContainerResourceMetrics[dockerContainerName] = SubContainerResourceMetrics(
                    psContainer.containerName.directoryContainer,
                    psContainer.resourceLimits.hasStorage() && fileSystem.supportsQuotas(),
                    appConfig.subContainerResourceUsageMetricIntervalMs,
                    appConfig.subContainerResourceSpaceUsageMetricIntervalMs,
            ) { includeDiskUsage ->
                buildContainerResourceUsage(psContainer, includeDiskUsage)
            }
        }
    }

    private fun buildContainerResourceUsage(psContainer: PostchainContainer, includeDiskUsage: Boolean): ContainerResourceUsage {

        val containerStats = dockerClient
                .statsCmd(psContainer.containerId)
                .withNoStream(true)
                .asyncExecAwaitSingleResponse()

        val fsLimits = if (includeDiskUsage)
            fileSystem.getCurrentLimitsInfo(psContainer.containerName, psContainer.resourceLimits)
        else
            null

        return ContainerResourceUsage.create(containerStats, fsLimits)
    }

    private fun unregisterSubContainerResourceMetrics(dockerContainer: String) {
        subContainerResourceMetrics[dockerContainer]?.close()
    }
}
