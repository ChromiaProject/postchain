package net.postchain.common.data

import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByteArrayKeyTest {

    val x = "1234567890"
    val bak = ByteArrayKey(x.hexStringToByteArray())

    @Test
    fun convertTest() {
        val hexShort = bak.shortString()
        assertEquals("1234:90", hexShort)
    }
}