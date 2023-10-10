package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

internal class GtvParserTest {


    @ParameterizedTest
    @MethodSource("testObjects")
    fun parseGtv(input: Gtv) {
        assertEquals(input, GtvParser.parse(input.toString()))
    }

    @ParameterizedTest
    @MethodSource("wrongFormat")
    fun parseGtvThrows(str: String){
        assertThrows<IllegalArgumentException> {
            GtvParser.parse(str)
        }
    }

    @Test
    fun testStringsContainingEscapedEquals() {
        val escapedEquals = GtvParser.parse("{a=contains\\=equals}")
        assertEquals(gtv("a" to gtv("contains=equals")), escapedEquals)

        // Multiple
        val multipleEscapedEquals = GtvParser.parse("{a=contains\\=equals\\=twice}")
        assertEquals(gtv("a" to gtv("contains=equals=twice")), multipleEscapedEquals)

        // Verify that I can still have a string value that contains "\="
        val escapedEqualsWithPrecedingBackslash = GtvParser.parse("{a=contains\\\\=equals}")
        assertEquals(gtv("a" to gtv("contains\\=equals")), escapedEqualsWithPrecedingBackslash)
    }

    companion object {
        @JvmStatic
        fun testObjects() = arrayOf(
                arrayOf(GtvNull),
                arrayOf(gtv(1)),
                arrayOf(gtv(BigInteger.ONE)),
                arrayOf(gtv(true)),
                arrayOf(gtv("Baloo")),
                arrayOf(gtv("AB".hexStringToByteArray())),
                arrayOf(gtv(gtv(3))),
                arrayOf(gtv(gtv("0,0"))),
                arrayOf(gtv("a" to gtv("0,0"))),
                arrayOf(gtv("a" to gtv(3), "b" to gtv("mega"))),
                arrayOf(gtv(gtv(1), gtv(gtv(2), gtv(3)))),
                arrayOf(gtv(GtvNull, gtv(gtv("AB".hexStringToByteArray()), gtv("foo")), gtv(gtv(1), gtv(2)))),
                arrayOf(gtv(gtv("a" to gtv(2)), gtv("b" to gtv(3)))),
                arrayOf(gtv("a" to gtv(gtv(1), gtv(2)))),
                arrayOf(gtv("b" to gtv(1), "a" to gtv("b" to gtv(gtv(1), gtv("c" to gtv(1)))))),
                arrayOf(gtv(gtv(1), gtv("a" to gtv("AB".hexStringToByteArray()))))
        )

        @JvmStatic
        fun wrongFormat() = arrayOf(
                arrayOf("{a}"),
                arrayOf("{a, b=1}"),
                // Contains un-escaped equal sign in string value
                arrayOf("{name=provider;url=https://provider.com}"),
                // Invalid byte array
                arrayOf("{pubkey=x\"03D4C71C2B63F6CE7F4C29D91F651FE9AA3E936CCAD8FA73633A4315CB2CDCACEB\";name=\"provider\"}")
        )
    }
}