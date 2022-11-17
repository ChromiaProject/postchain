package net.postchain.gtv.yaml

import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal class GtvYaml2Test {


    @ParameterizedTest
    @MethodSource("scalarInput")
    fun gtvDictionaryTest(yaml: String, expectedGtv: Gtv) {
        val actual = GtvYaml2().load("v: $yaml")
        assertk.assert(actual["v"]).isEqualTo(expectedGtv)
    }

    @Test
    fun b() {
        class Binary(
                val b: ByteArray,
                val wb: WrappedByteArray
        )
        val actual = GtvYaml2().load<Binary>("b: 0xAB\nwb: 0xAC")
        assertContentEquals("AB".hexStringToByteArray(), actual.b)
        assertEquals("AC".hexStringToWrappedByteArray(), actual.wb)

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
                arrayOf("0xAB", gtv("AB".hexStringToByteArray())),
                arrayOf("\n  - 1 \n  - 2", gtv(gtv(1), gtv(2))),
                arrayOf("\n  a: 37", gtv(mapOf("a" to gtv(37))))
        )
    }
}