package net.postchain.client

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.parse.GtvParser
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class ClientUtilsTest {

    @ParameterizedTest
    @MethodSource("validData")
    fun validEncodingTest(input: String, expected: Gtv) {
        assertThat(GtvParser.parse(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("invalidData")
    fun invalidEncodingTest(input: String) {
        assertThrows<IllegalArgumentException> { GtvParser.parse(input) }
    }

    companion object {
        @JvmStatic
        fun validData() = listOf(
                arrayOf("foo", GtvString("foo")),
                arrayOf("\"foo\"", GtvString("foo")),
                arrayOf("123", GtvInteger(123)),
                arrayOf("x\"byte\"", gtv("byte".toByteArray())),
                arrayOf("x\"EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F\"", gtv("EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F".hexStringToByteArray())),
                arrayOf("[a,123]", gtv(listOf(gtv("a"), gtv(123)))),
                arrayOf("{a=b,b=123}", gtv(mapOf("a" to gtv("b"), "b" to gtv(123)))),
                arrayOf("{a=b}", gtv(mapOf("a" to gtv("b")))),
        )

        @JvmStatic
        fun invalidData() = listOf(
                "{a}",
                "{a,b}"
        )
    }
}