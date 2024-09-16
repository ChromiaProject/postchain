package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig
import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.messages.Container
import org.mandas.docker.client.messages.ContainerInfo

open class ContainerHandler(
        private val dockerClient: DockerClient,
        private val appConfig: AppConfig,
        private val fileSystem: FileSystem,
) {
    companion object : KLogging()

    protected val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)

    fun pullImage(imageSpec: String) {
        dockerClient.pull(imageSpec)
    }

    fun createDockerContainer(containerName: ContainerName, resourceLimits: ContainerResourceLimits,
                              readOnly: Boolean, image: String): String {
        val config = ContainerConfigFactory.createConfig(fileSystem, appConfig, containerNodeConfig,
                containerName, resourceLimits, readOnly, image)
        return dockerClient.createContainer(config, containerName.dockerContainer).id()!!
    }

    fun startContainer(containerId: String) {
        dockerClient.startContainer(containerId)
    }

    fun stopContainer(containerId: String) {
        dockerClient.stopContainer(containerId, 10)
    }

    fun findContainer(containerId: String): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.firstOrNull { it.hasName(containerId) }
    }

    /**
     * Tries to find host port mappings for [containerPorts] given [containerId].
     */
    fun findHostPorts(containerId: String, containerPorts: List<Int>): Map<Int, Int> {
        val info = dockerClient.inspectContainer(containerId)
        return containerPorts.associateWith {
            info.hostPortFor(it) ?: throw ProgrammerMistake("Container has no mapped port for $it")
        }
    }

    private fun ContainerInfo.hostPortFor(port: Int) = networkSettings()?.ports()?.get("${port}/tcp")
            ?.firstOrNull()?.hostPort()?.toInt()
}
