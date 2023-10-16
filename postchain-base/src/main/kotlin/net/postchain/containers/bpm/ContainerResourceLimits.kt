package net.postchain.containers.bpm

import net.postchain.containers.bpm.resources.ResourceLimit
import net.postchain.containers.bpm.resources.ResourceLimitType
import net.postchain.containers.bpm.resources.ResourceLimitType.*

/**
 * Implements Docker resource constraints
 * https://docs.docker.com/config/containers/resource_constraints/
 */
data class ContainerResourceLimits(
        private val resourceLimits: Map<ResourceLimitType, ResourceLimit>,
) {

    constructor(vararg resourceLimits: ResourceLimit) : this(
            resourceLimits.associateBy { ResourceLimit.limitType(it) }
    )

    companion object {
        fun default() = ContainerResourceLimits(emptyMap())
    }

    /**
     * CPU: Percent of cpus, corresponds to '--cpus' docker cli option
     * 10 == 0.1 cpu(s), 150 == 1.5 cpu(s)
     */
    fun hasCpu() = getOrDefault(CPU) > 0
    fun cpuPeriod() = 100_000L
    fun cpuQuota() = getOrDefault(CPU) * cpuPeriod() / 100L

    /**
     * RAM in MiB
     */
    fun hasRam() = getOrDefault(RAM) > 0L
    fun ramBytes() = getOrDefault(RAM) * 1024 * 1024L

    /**
     * Storage in MiB
     */
    fun hasStorage() = getOrDefault(STORAGE) > 0
    fun storageMb() = getOrDefault(STORAGE)
    fun hasExtraStorage() = getOrDefault(EXTRA_STORAGE) > 0
    fun extraStorageMb() = getOrDefault(EXTRA_STORAGE)

    /**
     * IO read/write limits in MiB/s
     */
    fun hasIoRead() = ioReadBytes() in 1..Int.MAX_VALUE
    fun ioReadBytes() = getOrDefault(IO_READ) * 1024 * 1024L
    fun hasIoWrite() = ioWriteBytes() in 1..Int.MAX_VALUE
    fun ioWriteBytes() = getOrDefault(IO_WRITE) * 1024 * 1024L

    private fun getOrDefault(key: ResourceLimitType) = resourceLimits[key]?.value ?: -1L
}
