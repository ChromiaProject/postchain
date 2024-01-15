package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

internal class GtvParserTest {
    
    @Test
    fun lexUnicode() {
        assertArrayEquals(arrayOf(Token.Companion.String("foo ካ bar")), lexer("\"foo \u12AB bar\"").toList().toTypedArray())
    }

    @ParameterizedTest
    @MethodSource("testObjects")
    fun parseGtvObject(input: Gtv) {
        assertEquals(input, GtvParser.parse(input.toString()))
    }

    @ParameterizedTest
    @MethodSource("correctFormat")
    fun parseGtvWorks(str: String, expected: Gtv) {
        assertEquals(expected, GtvParser.parse(str))
    }

    @ParameterizedTest
    @MethodSource("wrongFormat")
    fun parseGtvThrows(str: String) {
        assertThrows<IllegalArgumentException> {
            GtvParser.parse(str)
        }
    }

    companion object {
        @JvmStatic
        fun testObjects() = arrayOf(
                arrayOf(GtvNull),
                arrayOf(gtv(1)),
                arrayOf(gtv(BigInteger.ONE)),
                arrayOf(gtv(true)),
                arrayOf(gtv("Baloo")),
                arrayOf(gtv("""'foo' "bar" \baz \u12AB""")),
                arrayOf(gtv("AB".hexStringToByteArray())),
                arrayOf(gtv(listOf())),
                arrayOf(gtv(gtv(3))),
                arrayOf(gtv(gtv("0,0"))),
                arrayOf(gtv(mapOf())),
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
        fun correctFormat() = arrayOf(
                arrayOf("\"räksmörgås\"", GtvString("räksmörgås")),
                arrayOf("""["räksmörgås": 17]""", gtv(mapOf("räksmörgås" to gtv(17)))),
                arrayOf("[[][]]", gtv(listOf(gtv(listOf()), gtv(listOf())))),
                arrayOf("[nullnull]", gtv(listOf(GtvNull, GtvNull))),
                arrayOf("""["a":null"b":null]""", gtv(mapOf("a" to GtvNull, "b" to GtvNull)))
        )

        @JvmStatic
        fun wrongFormat() = arrayOf(
                arrayOf("{a}"),
                arrayOf("{a, b=1}"),
                // Contains un-escaped equal sign in string value
                arrayOf("{name=provider;url=https://provider.com}"),
                // Invalid byte array
                arrayOf("""{pubkey=x"03D4C71C2B63F6CE7F4C29D91F651FE9AA3E936CCAD8FA73633A4315CB2CDCACEB";name="provider"}""")
        )
    }
}