package net.postchain.containers.bpm

import net.postchain.containers.bpm.ResourceLimitType.*
import net.postchain.gtv.Gtv

enum class ResourceLimitType {

    CPU, RAM, STORAGE;

    companion object {
        fun from(type: String?): ResourceLimitType? = values().firstOrNull { it.name == type }
    }
}

sealed interface ResourceLimit {
    val value: Long

    companion object {
        fun limitType(resourceLimit: ResourceLimit): ResourceLimitType {
            return when (resourceLimit) {
                is Cpu -> CPU
                is Ram -> RAM
                is Storage -> STORAGE
            }
        }
    }
}

object ResourceLimitFactory {

    fun fromGtv(pair: Pair<String, Gtv>): ResourceLimit? {
        return ResourceLimitType.from(pair.first.uppercase())
                ?.let {
                    val value = pair.second.asInteger()
                    when (it) {
                        CPU -> Cpu(value)
                        RAM -> Ram(value)
                        STORAGE -> Storage(value)
                    }
                }
    }
}

@JvmInline
value class Cpu(override val value: Long) : ResourceLimit

@JvmInline
value class Ram(override val value: Long) : ResourceLimit

@JvmInline
value class Storage(override val value: Long) : ResourceLimit

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
     * RAM in Mb
     */
    fun hasRam() = getOrDefault(RAM) > 0L
    fun ramBytes() = getOrDefault(RAM) * 1024 * 1024L

    /**
     * Storage in Mb
     */
    fun hasStorage() = getOrDefault(STORAGE) > 0
    fun storageMb() = getOrDefault(STORAGE)

    private fun getOrDefault(key: ResourceLimitType) = resourceLimits[key]?.value ?: -1L
}
