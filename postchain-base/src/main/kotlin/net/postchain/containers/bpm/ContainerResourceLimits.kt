package net.postchain.containers.bpm

import net.postchain.containers.bpm.ContainerResourceLimits.ResourceLimitType.*

@JvmInline
value class Cpu(val value: Long)

@JvmInline
value class Ram(val value: Long)

@JvmInline
value class Storage(val value: Long)

/**
 * Implements Docker resource constraints
 * https://docs.docker.com/config/containers/resource_constraints/
 */
data class ContainerResourceLimits(
        private val containerResourceLimitsType: Map<ResourceLimitType, Long>,
) {

    constructor(vararg limits: Pair<ResourceLimitType, Long>) : this(mapOf(*limits))

    enum class ResourceLimitType {

        CPU, RAM, STORAGE;

        companion object {
            fun from(type: String?): ResourceLimitType? = values().firstOrNull { it.name == type }
        }
    }

    companion object {

        fun default() = ContainerResourceLimits(emptyMap())

        fun fromValues(cpu: Cpu, ram: Ram, storage: Storage) = ContainerResourceLimits(
                CPU to cpu.value, RAM to ram.value, STORAGE to storage.value
        )

    }

    /**
     * CPU: Percent of cpus, corresponds to '--cpus' docker cli option
     * 10 == 0.1 cpu(s), 150 == 1.5 cpu(s)
     */
    fun hasCpu() = getOrDefault(CPU) > 0
    fun cpuPeriod() = 100_000L
    fun cpuQuota() = getOrDefault(CPU) * cpuPeriod() / 100L

    /**
     * RAM in Mb
     */
    fun hasRam() = getOrDefault(RAM) > 0L
    fun ramBytes() = getOrDefault(RAM) * 1024 * 1024L

    /**
     * Storage in Mb
     */
    fun hasStorage() = getOrDefault(STORAGE) > 0
    fun storageMb() = getOrDefault(STORAGE)

    private fun getOrDefault(key: ResourceLimitType) = containerResourceLimitsType[key] ?: -1L
}
