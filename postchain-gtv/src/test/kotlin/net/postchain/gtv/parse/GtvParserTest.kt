package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.Assertions.assertEquals

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
                arrayOf(gtv("a" to gtv(3), "b" to gtv("mega")))
        )
    }
}