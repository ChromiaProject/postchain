package net.postchain.containers.bpm

import net.postchain.config.app.AppConfig

data class ContainerName private constructor(
        val directoryContainer: String,
        val dockerContainer: String,
) {

    val name = dockerContainer

    companion object {

        fun create(appConfig: AppConfig, directoryContainer: String): ContainerName {
            val dockerContainer = "n${appConfig.pubKey.take(8)}_${directoryContainer}"
            return ContainerName(directoryContainer, dockerContainer)
        }
    }

    override fun toString(): String {
        return dockerContainer
    }
}
