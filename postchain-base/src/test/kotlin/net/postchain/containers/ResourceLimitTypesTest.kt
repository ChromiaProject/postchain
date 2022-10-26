package net.postchain.containers

import net.postchain.containers.bpm.resources.*
import net.postchain.containers.bpm.resources.ResourceLimitType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceLimitTypesTest {

    @Test
    fun testResourceLimitType() {
        assertEquals(CPU, ResourceLimitType.from("CPU"))
        assertEquals(null, ResourceLimitType.from("cpu"))

        assertEquals(RAM, ResourceLimitType.from("RAM"))
        assertEquals(null, ResourceLimitType.from("ram"))

        assertEquals(STORAGE, ResourceLimitType.from("STORAGE"))
        assertEquals(null, ResourceLimitType.from("storage"))

        assertEquals(null, ResourceLimitType.from("foo"))
    }

    @Test
    fun testObjectResourceLimitType() {
        assertEquals(CPU, ResourceLimit.limitType(Cpu(1)))
        assertEquals(RAM, ResourceLimit.limitType(Ram(1)))
        assertEquals(STORAGE, ResourceLimit.limitType(Storage(1)))
    }

    @Test
    fun testResourceLimitFactory() {
        assertEquals(Cpu(10), ResourceLimitFactory.fromPair("cpu" to 10L))
        assertEquals(Cpu(11), ResourceLimitFactory.fromPair("CPU" to 11L))

        assertEquals(Ram(20), ResourceLimitFactory.fromPair("ram" to 20L))
        assertEquals(Ram(21), ResourceLimitFactory.fromPair("RAM" to 21L))

        assertEquals(Storage(30), ResourceLimitFactory.fromPair("storage" to 30L))
        assertEquals(Storage(31), ResourceLimitFactory.fromPair("STORAGE" to 31L))

        // unknown key
        assertEquals(null, ResourceLimitFactory.fromPair("foo" to 31L))
    }
}