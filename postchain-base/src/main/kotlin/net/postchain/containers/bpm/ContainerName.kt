package net.postchain.containers.bpm

data class ContainerName private constructor(
        val directoryContainer: String,
        val dockerContainer: String,
        val containerIID: Int
) {

    companion object {

        fun containerName(pubKey: String, directoryContainer: String, containerIID: Int): String {
            return "${pubKey.take(8)}-${directoryContainer}-${containerIID}"
        }

        fun create(pubKey: String, directoryContainer: String, containerIID: Int): ContainerName {
            val dockerContainer = containerName(pubKey, directoryContainer, containerIID)
            return ContainerName(directoryContainer, dockerContainer, containerIID)
        }
    }

    override fun toString(): String = dockerContainer
}
