package net.postchain.gtv

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.parse.GtvParser
import org.junit.jupiter.api.Test
import java.math.BigInteger

internal class PrettyTest {
    @Test
    fun `integers are properly formatted`() {
        assertFormat(GtvInteger(17), "17")
    }

    @Test
    fun `big integers are properly formatted`() {
        assertFormat(GtvBigInteger(BigInteger.valueOf(17)), "17L")
    }

    @Test
    fun `byte arrays are properly formatted`() {
        assertFormat(GtvByteArray("AB12".hexStringToByteArray()), """x"AB12"""")
    }

    @Test
    fun `null properly formatted`() {
        assertFormat(GtvNull, "null")
    }

    @Test
    fun `strings are properly formatted`() {
        assertFormat(GtvString("foo bar"), """"foo bar"""")
    }

    @Test
    fun `arrays are properly formatted`() {
        assertFormat(GtvArray(arrayOf(GtvString("AAA"), GtvInteger(17))),
                """[
            |  "AAA",
            |  17
            |]""".trimMargin())
    }

    @Test
    fun `singleton arrays are properly formatted`() {
        assertFormat(GtvArray(arrayOf(GtvString("AAA"))),
                """[
            |  "AAA"
            |]""".trimMargin())
    }

    @Test
    fun `nested arrays are properly formatted`() {
        assertFormat(GtvArray(arrayOf(GtvArray(arrayOf(GtvString("AAA"), GtvInteger(17))), GtvBigInteger(BigInteger.valueOf(4711)))),
                """[
            |  [
            |    "AAA",
            |    17
            |  ],
            |  4711L
            |]""".trimMargin())
    }

    @Test
    fun `empty arrays are properly formatted`() {
        assertFormat(GtvArray(arrayOf()), """[]""")
    }

    @Test
    fun `dictionaries are properly formatted`() {
        assertFormat(GtvDictionary.build(mapOf("a" to GtvString("AAA"), "b" to GtvInteger(17))), """[
            |  "a": "AAA",
            |  "b": 17
            |]""".trimMargin())
    }

    @Test
    fun `singleton dictionaries are properly formatted`() {
        assertFormat(GtvDictionary.build(mapOf("a" to GtvString("AAA"))),
                """[
            |  "a": "AAA"
            |]""".trimMargin())
    }

    @Test
    fun `nested dictionaries are properly formatted`() {
        assertFormat(GtvDictionary.build(mapOf("a" to GtvDictionary.build(mapOf("c" to GtvString("AAA"), "d" to GtvInteger(17))), "b" to GtvBigInteger(BigInteger.valueOf(4711)))),
                """[
            |  "a": [
            |    "c": "AAA",
            |    "d": 17
            |  ],
            |  "b": 4711L
            |]""".trimMargin())
    }

    @Test
    fun `empty dictionaries are properly formatted`() {
        assertFormat(GtvDictionary.build(mapOf()), """[:]""")
    }

    private fun assertFormat(gtv: Gtv, expectedString: String) {
        val formatted = gtv.pretty()
        assertThat(formatted).isEqualTo(expectedString)
        assertThat(GtvParser.parse(formatted)).isEqualTo(gtv)
    }
}
