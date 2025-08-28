package net.postchain.containers

import net.postchain.containers.bpm.resources.Cpu
import net.postchain.containers.bpm.resources.IoRead
import net.postchain.containers.bpm.resources.IoWrite
import net.postchain.containers.bpm.resources.Ram
import net.postchain.containers.bpm.resources.ResourceLimit
import net.postchain.containers.bpm.resources.ResourceLimitFactory
import net.postchain.containers.bpm.resources.ResourceLimitType
import net.postchain.containers.bpm.resources.ResourceLimitType.CPU
import net.postchain.containers.bpm.resources.ResourceLimitType.IO_READ
import net.postchain.containers.bpm.resources.ResourceLimitType.IO_WRITE
import net.postchain.containers.bpm.resources.ResourceLimitType.RAM
import net.postchain.containers.bpm.resources.ResourceLimitType.STORAGE
import net.postchain.containers.bpm.resources.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceLimitTypesTest {

    @Test
    fun testResourceLimitType() {
        assertEquals(CPU, ResourceLimitType.from("CPU"))
        assertEquals(null, ResourceLimitType.from("cpu"))

        assertEquals(RAM, ResourceLimitType.from("RAM"))
        assertEquals(null, ResourceLimitType.from("ram"))

        assertEquals(STORAGE, ResourceLimitType.from("STORAGE"))
        assertEquals(null, ResourceLimitType.from("storage"))

        assertEquals(IO_READ, ResourceLimitType.from("IO_READ"))
        assertEquals(null, ResourceLimitType.from("io_read"))

        assertEquals(IO_WRITE, ResourceLimitType.from("IO_WRITE"))
        assertEquals(null, ResourceLimitType.from("io_write"))

        assertEquals(null, ResourceLimitType.from("foo"))
    }

    @Test
    fun testObjectResourceLimitType() {
        assertEquals(CPU, ResourceLimit.limitType(Cpu(1)))
        assertEquals(RAM, ResourceLimit.limitType(Ram(1)))
        assertEquals(STORAGE, ResourceLimit.limitType(Storage(1)))
        assertEquals(IO_READ, ResourceLimit.limitType(IoRead(1)))
        assertEquals(IO_WRITE, ResourceLimit.limitType(IoWrite(1)))
    }

    @Suppress("AssertBetweenInconvertibleTypes")
    @Test
    fun testResourceLimitFactory() {
        assertEquals(Cpu(10), ResourceLimitFactory.fromPair("cpu" to 10L))
        assertEquals(Cpu(11), ResourceLimitFactory.fromPair("CPU" to 11L))

        assertEquals(Ram(20), ResourceLimitFactory.fromPair("ram" to 20L))
        assertEquals(Ram(21), ResourceLimitFactory.fromPair("RAM" to 21L))

        assertEquals(Storage(30), ResourceLimitFactory.fromPair("storage" to 30L))
        assertEquals(Storage(31), ResourceLimitFactory.fromPair("STORAGE" to 31L))

        assertEquals(IoRead(50), ResourceLimitFactory.fromPair("io_read" to 50L))
        assertEquals(IoRead(31), ResourceLimitFactory.fromPair("IO_READ" to 31L))

        assertEquals(IoWrite(50), ResourceLimitFactory.fromPair("io_write" to 50L))
        assertEquals(IoWrite(31), ResourceLimitFactory.fromPair("IO_WRITE" to 31L))

        // unknown key
        assertEquals(null, ResourceLimitFactory.fromPair("foo" to 31L))
    }
}