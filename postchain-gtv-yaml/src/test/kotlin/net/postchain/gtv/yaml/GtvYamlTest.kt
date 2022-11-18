package net.postchain.gtv.yaml

import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal class GtvYamlTest {


    @Test
    fun empty() {
        assertk.assert(GtvYaml().load("---")).isEqualTo(GtvNull)
    }

    @ParameterizedTest
    @MethodSource("allGtvTypes")
    fun gtvTest(yaml: String, expectedGtv: Gtv) {
        val actual = GtvYaml().load("v: $yaml")
        assertk.assert(actual["v"]).isEqualTo(expectedGtv)
    }

    @ParameterizedTest
    @MethodSource("allGtvTypes")
    fun classTest(yaml: String, expectedGtv: Gtv) {
        data class GtvClass(
                val g: Gtv
        )
        val actual = GtvYaml().load<GtvClass>("g: $yaml")
        assertk.assert(actual).isEqualTo(GtvClass(expectedGtv))
    }

    @Test
    fun byteArrayTest() {
        class Binary(
                val b: ByteArray,
                val wb: WrappedByteArray
        )

        val actual = GtvYaml().load<Binary>("b: 0xAB\nwb: 0xAC")
        assertContentEquals("AB".hexStringToByteArray(), actual.b)
        assertEquals("AC".hexStringToWrappedByteArray(), actual.wb)

    }

    @Test
    fun nestedTest() {
        val actual = GtvYaml().load("""
            a:
              b:
                c: 1
        """.trimIndent())
        assertk.assert(actual).isEqualTo(gtv("a" to gtv("b" to gtv("c" to gtv(1)))))
    }


    @Test
    fun allPrimitivesTest() {

        class AllPrimitives {
            var i: Int? = null
            var l: Long? = null
            var bo: Boolean? = null
            var ba: ByteArray? = null
            var wba: WrappedByteArray? = null
            var s: String? = null
            var gtv: Gtv? = null
            var li: List<Long>? = null
            var se: Set<String>? = null
            var ma: Map<String, Gtv>? = null
            var def1: String = "default1"
            var def2: String = "default2"
        }

        val actual = GtvYaml().load<AllPrimitives>("""
            i: 1
            l: 2
            bo: true
            ba: 0x12
            wba: 0x13
            s: foo
            gtv: 12
            li: 
              - 1
              - 2
            se:
              - a
              - b
            ma:
              k1: v1
              k2: 5
            def1: "overridden"
        """.trimIndent())
        assertk.assert(actual.i).isEqualTo(1)
        assertk.assert(actual.l).isEqualTo(2L)
        assertk.assert(actual.bo).isEqualTo(true)
        assertContentEquals("12".hexStringToByteArray(), actual.ba)
        assertk.assert(actual.wba).isEqualTo("13".hexStringToWrappedByteArray())
        assertk.assert(actual.s).isEqualTo("foo")
        assertk.assert(actual.gtv).isEqualTo(gtv(12))
        assertk.assert(actual.li).isEqualTo(listOf(1L, 2L))
        assertk.assert(actual.se).isEqualTo(setOf("a", "b"))
        assertk.assert(actual.ma).isEqualTo(mapOf("k1" to gtv("v1"), "k2" to gtv(5)))
        assertk.assert(actual.def1).isEqualTo("overridden")
        assertk.assert(actual.def2).isEqualTo("default2")
    }

    companion object {
        @JvmStatic
        fun allGtvTypes() = arrayOf(
                arrayOf("1", gtv(1)),
                arrayOf("1000000000000000000", gtv(1000000000000000000)),
                arrayOf("10000000000000000000", gtv(BigInteger("10000000000000000000"))),
                arrayOf("true", gtv(true)),
                arrayOf("test", gtv("test")),
                arrayOf("1.2", gtv("1.2")),
                arrayOf("0xAB", gtv("AB".hexStringToByteArray())),
                arrayOf("\n  - 1 \n  - 2", gtv(gtv(1), gtv(2))),
                arrayOf("\n  a: 37", gtv(mapOf("a" to gtv(37)))),
                arrayOf("null", GtvNull),
        )
    }
}
