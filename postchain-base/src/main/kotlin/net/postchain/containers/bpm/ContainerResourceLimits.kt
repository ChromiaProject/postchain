package net.postchain.containers.bpm

data class ContainerResourceLimits(
        val ram: Long,           // Mb
        val cpu: Long,           // TODO: [et]: ?
        val storage: Long        // Mb
) {

    constructor(ram: Long?, cpu: Long?, storage: Long?) :
            this(ram ?: -1, cpu ?: -1, storage ?: -1)

    companion object {
        const val RAM_KEY = "ram"
        const val CPU_KEY = "cpu"
        const val STORAGE_KEY = "storage"

        fun default() = ContainerResourceLimits(-1, -1, -1)
    }
}
