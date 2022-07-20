package net.postchain.containers.bpm

import net.postchain.config.app.AppConfig

data class ContainerName(
        val name: String,
        val directoryContainer: String,
) {

    companion object {

        fun create(appConfig: AppConfig, directoryContainer: String): ContainerName {
            val name = "n${appConfig.pubKey.take(8)}_${directoryContainer}"
            return ContainerName(name, directoryContainer)
        }
    }

    override fun toString(): String {
        return name
    }
}
