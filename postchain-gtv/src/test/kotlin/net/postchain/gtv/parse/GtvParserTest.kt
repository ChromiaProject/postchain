package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class GtvParserTest {


    @ParameterizedTest
    @MethodSource("testObjects")
    fun parseGtv(input: Gtv) {
        assertEquals(input, GtvParser.parse(input.toString()))
    }

    companion object {
        @JvmStatic
        fun testObjects() = arrayOf(
                arrayOf(GtvNull),
                arrayOf(gtv(1)),
                arrayOf(gtv(true)),
                arrayOf(gtv("Baloo")),
                arrayOf(gtv("AB".hexStringToByteArray())),
                arrayOf(gtv(gtv(3))),
                arrayOf(gtv("a" to gtv("hej"))),
                arrayOf(gtv("a" to gtv(3), "b" to gtv("mega"))),
                arrayOf(gtv(gtv(1), gtv(gtv(2), gtv(3)))),
                arrayOf(gtv(GtvNull, gtv(gtv("AB".hexStringToByteArray()), gtv("foo")), gtv(gtv(1), gtv(2)))),
                arrayOf(gtv("a" to gtv(gtv(1), gtv(2)))),
                arrayOf(gtv("b" to gtv(1), "a" to gtv("b" to gtv(gtv(1), gtv("c" to gtv(1)))))),
                arrayOf(gtv(gtv(1), gtv("a" to gtv("AB".hexStringToByteArray()))))
        )
    }
}