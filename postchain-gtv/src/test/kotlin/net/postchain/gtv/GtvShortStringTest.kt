// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.gtv

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.math.BigInteger

class GtvShortStringTest {

    @Test
    fun `strings are truncated`() {
        val gtvString = GtvString("x".repeat(100))
        assertThat(gtvString.shortString()).isEqualTo("\"${"x".repeat(64)}...")
    }

    @Test
    fun `big integers are truncated`() {
        val gtvBigInteger = GtvBigInteger(BigInteger(ByteArray(32) { it.toByte() } ))
        assertThat(gtvBigInteger.shortString()).isEqualTo("very_big_integer")
    }

    @Test
    fun `byte arrays are truncated`() {
        val gtvByteArray = GtvByteArray(ByteArray(64))
        assertThat(gtvByteArray.shortString()).isEqualTo("long_byte_array")
    }

    @Test
    fun `arrays are truncated`() {
        val gtvArray = GtvArray(Array(32) { GtvString("foo") })
        assertThat(gtvArray.shortString()).isEqualTo("long_array")
    }

    @Test
    fun `dictionaries are truncated`() {
        val gtvDictionary = GtvDictionary.build(Array(32) { it.toString() to GtvInteger(it.toLong()) }.toMap())
        assertThat(gtvDictionary.shortString()).isEqualTo("long_dict")
    }
}
