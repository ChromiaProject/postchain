package net.postchain.containers.bpm

data class ContainerJarExtensionInfo(val name: String, val hash: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContainerJarExtensionInfo

        if (name != other.name) return false
        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }
}
