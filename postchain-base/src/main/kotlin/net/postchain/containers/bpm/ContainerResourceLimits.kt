package net.postchain.containers.bpm

/**
 * Implements Docker resource constraints
 * https://docs.docker.com/config/containers/resource_constraints/
 */
data class ContainerResourceLimits(
        private val resourceLimits: Map<String, Long>
) {

    companion object {
        const val KEY_RAM = "ram"
        const val KEY_CPU = "cpu"
        const val KEY_STORAGE = "storage"

        fun default() = ContainerResourceLimits(emptyMap())

        fun fromValues(ram: Long, cpu: Long, storage: Long) = ContainerResourceLimits(mapOf(
                KEY_RAM to ram, KEY_CPU to cpu, KEY_STORAGE to storage
        ))

    }

    /**
     * RAM in Mb
     */
    fun hasRam() = getOrDefault(KEY_RAM) > 0L
    fun ramBytes() = getOrDefault(KEY_RAM) * 1024 * 1024L

    /**
     * CPU: Percent of cpus, corresponds to '--cpus' docker cli option
     * 10 == 0.1 cpu(s), 150 == 1.5 cpu(s)
     */
    fun hasCpu() = getOrDefault(KEY_CPU) > 0
    fun cpuPeriod() = 100_000L
    fun cpuQuota() = getOrDefault(KEY_CPU) * cpuPeriod() / 100L

    /**
     * Storage in Mb
     */
    fun hasStorage() = getOrDefault(KEY_STORAGE) > 0
    fun storageMb() = getOrDefault(KEY_STORAGE)

    private fun getOrDefault(key: String) = resourceLimits[key] ?: -1L
}
