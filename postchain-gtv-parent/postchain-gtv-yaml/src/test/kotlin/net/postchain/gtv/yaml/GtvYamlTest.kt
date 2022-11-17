package net.postchain.gtv.yaml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

internal class GtvYamlTest {

    @ParameterizedTest
    @MethodSource("scalarInput")
    fun gtvDictionaryTest(yaml: String, expectedGtv: Gtv) {
        val actual = GtvYaml().load<Gtv>("v: $yaml")
        assert(actual).isEqualTo(gtv("v" to expectedGtv))
    }

    @Test
    fun gtvArrayTest() {
        val actual = GtvYaml().load<Gtv>("""
             - 1
             - 2
        """.trimIndent())
        assert(actual).isEqualTo(gtv(gtv(1), gtv(2)))
    }

    @Test
    fun nestedTest() {
        val actual = GtvYaml().load<Gtv>("""
            a:
              b:
                c: 1
        """.trimIndent())
        assert(actual).isEqualTo(gtv("a" to gtv("b" to gtv("c" to gtv(1)))))
    }

    companion object {
        @JvmStatic
        fun scalarInput() = arrayOf(
                arrayOf("1", gtv(1)),
                arrayOf("1000000000000000000", gtv(1000000000000000000)),
                arrayOf("10000000000000000000", gtv(BigInteger("10000000000000000000"))),
                arrayOf("true", gtv(true)),
                arrayOf("test", gtv("test")),
                arrayOf("1.2", gtv("1.2")),
                arrayOf("!!binary AB", gtv("AB".hexStringToByteArray())),
                arrayOf("0xAB", gtv("AB".hexStringToByteArray())),
                arrayOf("\n  - 1 \n  - 2", gtv(gtv(1), gtv(2)))
        )
    }
}
