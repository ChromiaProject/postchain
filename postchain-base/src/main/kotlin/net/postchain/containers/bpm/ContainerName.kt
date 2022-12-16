package net.postchain.containers.bpm

data class ContainerName private constructor(
        val directoryContainer: String,
        val dockerContainer: String,
) {

    val name = dockerContainer

    companion object {

        fun create(masterHost: String, directoryContainer: String): ContainerName {
            val dockerContainer = "${masterHost}${directoryContainer}"
            return ContainerName(directoryContainer, dockerContainer)
        }
    }

    override fun toString(): String {
        return dockerContainer
    }
}
