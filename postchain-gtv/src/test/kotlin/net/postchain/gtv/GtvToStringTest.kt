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
    fun `empty arrays are properly formatted`() {
        val gtvArray = GtvArray(arrayOf())
        assertThat(gtvArray.toString()).isEqualTo("""[]""")
    }
}
