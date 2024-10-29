package net.postchain.containers.bpm

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PullResponseItem
import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.docker.DockerTools.listSubContainersCmd
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.infra.ContainerNodeConfig

open class ContainerHandler(
        private val dockerClient: DockerClient,
        private val appConfig: AppConfig,
        private val fileSystem: FileSystem,
) {
    companion object : KLogging()

    protected val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)

    fun pullImage(imageSpec: String) {
        dockerClient.pullImageCmd(imageSpec).exec(object : ResultCallback.Adapter<PullResponseItem>() {
            override fun onError(throwable: Throwable?) {
                throw ProgrammerMistake("Failed to pull docker image: $imageSpec: ${throwable?.message}")
            }
        }
        ).awaitCompletion()
    }

    fun createDockerContainer(containerName: ContainerName, resourceLimits: ContainerResourceLimits,
                              readOnly: Boolean, image: String): String {
        val createContainerCmd = dockerClient.createContainerCmd(image)
        ContainerConfigFactory.setConfig(createContainerCmd, fileSystem, appConfig, containerNodeConfig,
                containerName, resourceLimits, readOnly)
        return createContainerCmd.exec().id!!
    }

    fun startContainer(containerId: String) {
        dockerClient.startContainerCmd(containerId).exec()
    }

    fun stopContainer(containerId: String) {
        dockerClient.stopContainerCmd(containerId).withTimeout(10).exec()
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
}
