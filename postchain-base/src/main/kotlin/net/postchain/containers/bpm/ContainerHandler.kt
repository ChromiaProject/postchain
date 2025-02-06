package net.postchain.containers.bpm

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.docker.DockerTools.asyncExecAwaitSingleResponse
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.docker.DockerTools.listSubContainersCmd
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.metrics.SubContainerResourceMetrics

open class ContainerHandler(
        private val dockerClient: DockerClient,
        private val appConfig: AppConfig,
        private val fileSystem: FileSystem,
        private val subContainerResourceMetrics: MutableMap<String, SubContainerResourceMetrics> = mutableMapOf(),
) {
    companion object : KLogging()

    protected val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)

    fun pullImage(imageSpec: String) {
        dockerClient.pullImageCmd(imageSpec).asyncExecAwaitSingleResponse()
    }

    fun createDockerContainer(containerName: ContainerName, resourceLimits: ContainerResourceLimits,
                              readOnly: Boolean, image: String): String {
        val createContainerCmd = dockerClient.createContainerCmd(image)
        ContainerConfigFactory.setConfig(createContainerCmd, fileSystem, appConfig, containerNodeConfig,
                containerName, resourceLimits, readOnly)
        return createContainerCmd.exec().id!!
    }

    fun startContainer(psContainer: PostchainContainer) {
        dockerClient.startContainerCmd(psContainer.containerId!!).exec()
        registerSubContainerResourceMetrics(psContainer)
    }

    fun stopContainer(psContainer: PostchainContainer) {
        unregisterSubContainerResourceMetrics(psContainer.containerId!!)
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
            subContainerResourceMetrics[psContainer.containerId!!] = SubContainerResourceMetrics(
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

    private fun unregisterSubContainerResourceMetrics(containerId: String) {
        subContainerResourceMetrics[containerId]?.close()
    }
}
