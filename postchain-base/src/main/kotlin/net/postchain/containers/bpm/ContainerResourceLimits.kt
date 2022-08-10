package net.postchain.containers.bpm

import net.postchain.containers.bpm.ContainerResourceLimits.ResourceLimit.*

/**
 * Implements Docker resource constraints
 * https://docs.docker.com/config/containers/resource_constraints/
 */
data class ContainerResourceLimits(
        private val resourceLimits: Map<ResourceLimit, Long>
) {

    constructor(vararg limits: Pair<ResourceLimit, Long>) : this(mapOf(*limits))

    enum class ResourceLimit { RAM, CPU, STORAGE }

    companion object {

        fun default() = ContainerResourceLimits(emptyMap())

        fun fromValues(ram: Long, cpu: Long, storage: Long) = ContainerResourceLimits(
                RAM to ram, CPU to cpu, STORAGE to storage
        )

    }

    /**
     * RAM in Mb
     */
    fun hasRam() = getOrDefault(RAM) > 0L
    fun ramBytes() = getOrDefault(RAM) * 1024 * 1024L

    /**
     * CPU: Percent of cpus, corresponds to '--cpus' docker cli option
     * 10 == 0.1 cpu(s), 150 == 1.5 cpu(s)
     */
    fun hasCpu() = getOrDefault(CPU) > 0
    fun cpuPeriod() = 100_000L
    fun cpuQuota() = getOrDefault(CPU) * cpuPeriod() / 100L

    /**
     * Storage in Mb
     */
    fun hasStorage() = getOrDefault(STORAGE) > 0
    fun storageMb() = getOrDefault(STORAGE)

    private fun getOrDefault(key: ResourceLimit) = resourceLimits[key] ?: -1L
}
