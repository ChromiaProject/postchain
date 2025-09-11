package net.postchain.containers.bpm.resources

import net.postchain.containers.bpm.resources.ResourceLimitType.CPU
import net.postchain.containers.bpm.resources.ResourceLimitType.IO_READ
import net.postchain.containers.bpm.resources.ResourceLimitType.IO_WRITE
import net.postchain.containers.bpm.resources.ResourceLimitType.RAM
import net.postchain.containers.bpm.resources.ResourceLimitType.STORAGE

enum class ResourceLimitType {

    CPU, RAM, STORAGE, IO_READ, IO_WRITE;

    companion object {
        fun from(type: String?): ResourceLimitType? = ResourceLimitType.entries.firstOrNull { it.name == type }
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
                is IoRead -> IO_READ
                is IoWrite -> IO_WRITE
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

@JvmInline
value class IoRead(override val value: Long) : ResourceLimit

@JvmInline
value class IoWrite(override val value: Long) : ResourceLimit


object ResourceLimitFactory {

    fun fromPair(pair: Pair<String, Long>): ResourceLimit? {
        return ResourceLimitType.from(pair.first.uppercase())
                ?.let {
                    val value = pair.second
                    when (it) {
                        CPU -> Cpu(value)
                        RAM -> Ram(value)
                        STORAGE -> Storage(value)
                        IO_READ -> IoRead(value)
                        IO_WRITE -> IoWrite(value)
                    }
                }
    }
}
