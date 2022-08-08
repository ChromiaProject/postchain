package net.postchain.containers.bpm

/**
 * Implements Docker resource constraints
 * https://docs.docker.com/config/containers/resource_constraints/
 */
data class ContainerResourceLimits(
        // Mb
        private val ram: Long,
        // Percent of cpus, corresponds to '--cpus' docker cli option
        // 10 == 0.1 cpu(s), 150 == 1.5 cpu(s)
        private val cpu: Long,
        // Mb
        private val storage: Long
) {

    // ram
    fun hasRam() = ram > 0
    fun ramBytes() = ram * 1024 * 1024L

    // cpu
    fun hasCpu() = cpu > 0
    fun cpuPeriod() = 100_000L
    fun cpuQuota() = cpu * cpuPeriod() / 100L

    // storage
    fun hasStorage() = storage > 0
    fun storageMb() = storage

    constructor(ram: Long?, cpu: Long?, storage: Long?) :
            this(ram ?: -1, cpu ?: -1, storage ?: -1)

    companion object {
        const val RAM_KEY = "ram"
        const val CPU_KEY = "cpu"
        const val STORAGE_KEY = "storage"

        fun default() = ContainerResourceLimits(-1, -1, -1)
    }

}
