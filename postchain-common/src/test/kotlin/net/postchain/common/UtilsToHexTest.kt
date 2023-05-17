// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class UtilsToHexTest {

    @Test
    fun toHex_empty_successfully() {
        val actual = byteArrayOf().toHex()
        val expected = ""

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun toHex_good_hex_in_array_successfully() {
        val actual = byteArrayOf(0x01, 0x02, 0x0A, 0xF).toHex()
        val expected = "01020a0F"

        assertThat(actual).isEqualTo(expected, ignoreCase = true)
    }

    @Test
    fun toHex_negative_in_array_successfully() {
        val actual = byteArrayOf(0xFF.toByte(), 0xFE.toByte()).toHex()
        val expected = "FFFE"

        assertThat(actual).isEqualTo(expected, ignoreCase = true)
    }
}