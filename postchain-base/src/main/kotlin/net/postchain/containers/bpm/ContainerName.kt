package net.postchain.containers.bpm

import net.postchain.config.app.AppConfig

data class ContainerName(
        val name: String,
        val directory: String
) {

    companion object {

        fun create(appConfig: AppConfig, directory: String): ContainerName {
            val name = "n${appConfig.pubKey.asString.take(8)}_${directory}"
            return ContainerName(name, directory)
        }

        fun createGroupName(groupName: String) = ContainerName(groupName, "")

    }

    fun isGroup(groupName: String): Boolean = name == groupName

    override fun toString(): String {
        return name
    }
}
