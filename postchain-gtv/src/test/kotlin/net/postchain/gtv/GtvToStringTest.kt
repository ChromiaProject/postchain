// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.gtv

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test
import java.math.BigInteger

class GtvToStringTest {

    @Test
    fun `strings are quoted and escaped`() {
        val gtvString = GtvString("""'foo' "bar" \baz""")
        assertThat(gtvString.toString()).isEqualTo(""""\'foo\' \"bar\" \\baz"""")
    }

    @Test
    fun `integers are properly formatted`() {
        val gtvInteger = GtvInteger(17)
        assertThat(gtvInteger.toString()).isEqualTo("17")
    }

    @Test
    fun `big integers are properly formatted`() {
        val gtvBigInteger = GtvBigInteger(BigInteger.TEN)
        assertThat(gtvBigInteger.toString()).isEqualTo("10L")
    }

    @Test
    fun `byte arrays are properly formatted`() {
        val gtvByteArray = GtvByteArray("1234abcd".hexStringToByteArray())
        assertThat(gtvByteArray.toString()).isEqualTo("""x"1234ABCD"""")
    }

    @Test
    fun `nulls are properly formatted`() {
        assertThat(GtvNull.toString()).isEqualTo("null")
    }

    @Test
    fun `arrays are properly formatted`() {
        val gtvArray = GtvArray(arrayOf(GtvString("AAA"), GtvInteger(17)))
        assertThat(gtvArray.toString()).isEqualTo("""["AAA", 17]""")
    }

    @Test
    fun `singleton arrays are properly formatted`() {
        val gtvArray = GtvArray(arrayOf(GtvString("AAA")))
        assertThat(gtvArray.toString()).isEqualTo("""["AAA"]""")
    }

    @Test
    fun `empty arrays are properly formatted`() {
        val gtvArray = GtvArray(arrayOf())
        assertThat(gtvArray.toString()).isEqualTo("""[]""")
    }

    @Test
    fun `dictionaries are properly formatted`() {
        val gtvDictionary = GtvDictionary.build(mapOf("a" to GtvString("AAA"), "b" to GtvInteger(17)))
        assertThat(gtvDictionary.toString()).isEqualTo("""["a": "AAA", "b": 17]""")
    }

    @Test
    fun `singleton dictionaries are properly formatted`() {
        val gtvDictionary = GtvDictionary.build(mapOf("a" to GtvString("AAA")))
        assertThat(gtvDictionary.toString()).isEqualTo("""["a": "AAA"]""")
    }

    @Test
    fun `empty dictionaries are properly formatted`() {
        val gtvDictionary = GtvDictionary.build(mapOf())
        assertThat(gtvDictionary.toString()).isEqualTo("""[:]""")
    }

    @Test
    fun `dictionary keys are escaped`() {
        val gtvDictionary = GtvDictionary.build(mapOf("a\"b" to GtvString("AAA")))
        assertThat(gtvDictionary.toString()).isEqualTo("""["a\"b": "AAA"]""")
    }
}
