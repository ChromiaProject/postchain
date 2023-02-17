package net.postchain.containers.bpm

import net.postchain.config.app.AppConfig

data class ContainerName private constructor(
        val directoryContainer: String,
        val dockerContainer: String,
        val containerIID: Int
) {
    val name = dockerContainer

    companion object {
        fun create(appConfig: AppConfig, directoryContainer: String, containerIID: Int): ContainerName {
            val dockerContainer = "${appConfig.pubKey.take(8)}-${directoryContainer}-${containerIID}"
            return ContainerName(directoryContainer, dockerContainer, containerIID)
        }
    }

    override fun toString(): String = dockerContainer
}
