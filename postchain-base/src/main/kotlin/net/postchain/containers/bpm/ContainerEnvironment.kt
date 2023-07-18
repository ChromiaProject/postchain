package net.postchain.containers.bpm

import mu.KLogging
import mu.withLoggingContext
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.containers.bpm.docker.DockerTools
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.logging.CONTAINER_NAME_TAG
import org.mandas.docker.client.DockerClient

object ContainerEnvironment : KLogging() {

    lateinit var dockerClient: DockerClient
        private set

    fun init(appConfig: AppConfig) {
        if (::dockerClient.isInitialized) {
            logger.warn("Container environment is already initialized")
            return
        }
        dockerClient = DockerClientFactory.create()

        try {
            dockerClient.ping()
        } catch (e: Exception) {
            logger.error("Unable to access Docker daemon: $e")
        }
        try {
            removeContainersIfExist(appConfig)
        } catch (e: Exception) {
            logger.error("Unable to list/remove containers: $e")
        }
    }

    private fun removeContainersIfExist(appConfig: AppConfig) {
        val config = ContainerNodeConfig.fromAppConfig(appConfig)

        val toStop = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).filter {
            (it.labels() ?: emptyMap())[POSTCHAIN_MASTER_PUBKEY] == config.masterPubkey
        }

        if (toStop.isNotEmpty()) {
            logger.warn {
                "Containers found to be removed (${toStop.size}): ${toStop.joinToString(transform = DockerTools::containerName)}"
            }

            toStop.forEach {
                withLoggingContext(CONTAINER_NAME_TAG to DockerTools.containerName(it).drop(1)) {
                    try {
                        dockerClient.stopContainer(it.id(), 20)
                        logger.info { "Container has been stopped: ${DockerTools.containerName(it)} / ${DockerTools.shortContainerId(it.id())}" }
                    } catch (e: Exception) {
                        logger.error("Can't stop container: " + it.id(), e)
                    }

                    try {
                        dockerClient.removeContainer(it.id(), DockerClient.RemoveContainerParam.forceKill())
                        logger.info { "Container has been removed: ${DockerTools.containerName(it)} / ${DockerTools.shortContainerId(it.id())}" }
                    } catch (e: Exception) {
                        logger.error("Can't remove container: " + it.id(), e)
                    }
                }
            }
        }
    }

}