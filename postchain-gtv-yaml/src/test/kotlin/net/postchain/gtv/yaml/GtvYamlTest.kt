package net.postchain.gtv.yaml

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.isContentEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

internal class GtvYamlTest {

    @Test
    fun `big integer format`() {
        assertThat(BIG_INTEGER_FORMAT.matcher("").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("L").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("abcL").matches()).isFalse()

        assertThat(BIG_INTEGER_FORMAT.matcher("-1").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("+1").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("1").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("-17").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("+17").matches()).isFalse()
        assertThat(BIG_INTEGER_FORMAT.matcher("17").matches()).isFalse()

        assertThat(BIG_INTEGER_FORMAT.matcher("\"17L\"").matches()).isFalse()

        assertThat(BIG_INTEGER_FORMAT.matcher("-1L").matches()).isTrue()
        assertThat(BIG_INTEGER_FORMAT.matcher("+1L").matches()).isTrue()
        assertThat(BIG_INTEGER_FORMAT.matcher("1L").matches()).isTrue()
        assertThat(BIG_INTEGER_FORMAT.matcher("-17L").matches()).isTrue()
        assertThat(BIG_INTEGER_FORMAT.matcher("+17L").matches()).isTrue()
        assertThat(BIG_INTEGER_FORMAT.matcher("17L").matches()).isTrue()
    }

    @Test
    fun `byte array format`() {
        assertThat(BYTE_ARRAY_FORMAT.matcher("").matches()).isFalse()
        assertThat(BYTE_ARRAY_FORMAT.matcher("xABCD").matches()).isFalse()
        assertThat(BYTE_ARRAY_FORMAT.matcher("\"ABCD\"").matches()).isFalse()

        assertThat(BYTE_ARRAY_FORMAT.matcher("\"x\"\"\"").matches()).isFalse()
        assertThat(BYTE_ARRAY_FORMAT.matcher("\"x\"AB12\"\"").matches()).isFalse()

        assertThat(BYTE_ARRAY_FORMAT.matcher("x\"\"").matches()).isTrue()
        assertThat(BYTE_ARRAY_FORMAT.matcher("x\"AB12\"").matches()).isTrue()
    }

    @Test
    fun empty() {
        assertThat(GtvYaml().load("---")).isEqualTo(GtvNull)
    }

    @ParameterizedTest
    @MethodSource("allGtvTypes")
    fun gtvTest(yaml: String, expectedGtv: Gtv) {
        val yml = "v: $yaml"
        val actual = GtvYaml().load(yml)
        assertThat(actual["v"]).isEqualTo(expectedGtv)
    }

    @ParameterizedTest
    @MethodSource("allGtvTypes")
    fun classTest(yaml: String, expectedGtv: Gtv) {
        data class GtvClass(
                @param:Name("g") val g: Gtv
        )

        val actual = GtvObjectMapper.fromGtv(GtvYaml().load("g: $yaml"), GtvClass::class)
        assertThat(actual).isEqualTo(GtvClass(expectedGtv))
    }

    @Test
    fun byteArrayTest() {
        class Binary(
                @param:Name("b") val b: ByteArray,
                @param:Name("wb") val wb: WrappedByteArray
        )

        val actual = GtvObjectMapper.fromGtv(GtvYaml().load("b: x\"AB\"\nwb: x\"AC\""), Binary::class)
        assertThat(actual.b).isContentEqualTo("AB".hexStringToByteArray())
        assertThat(actual.wb).isEqualTo("AC".hexStringToWrappedByteArray())
    }

    @Test
    fun nestedTest() {
        val actual = GtvYaml().load("""
            a:
              b:
                c: 1
        """.trimIndent())
        assertThat(actual).isEqualTo(gtv("a" to gtv("b" to gtv("c" to gtv(1)))))
    }

    private val allPrimitivesYaml = """
                l: 2
                nol: "17"
                bi: 1234L
                nobi: "1234L"
                t: true
                nt: "true"
                f: false
                nf: "false"
                ba: x"12"
                wba: x"13"
                noba: "x\"1234ABCD\""
                s: foo
                n: null
                nn: "null"
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
            """.trimIndent()

    @Test
    fun allPrimitivesTest() {
        data class AllPrimitives(
                @param:Name("l") val l: Long,
                @param:Name("nol") val nol: String,
                @param:Name("bi") val bi: BigInteger,
                @param:Name("nobi") val nobi: String,
                @param:Name("t") val t: Boolean,
                @param:Name("nt") val nt: String,
                @param:Name("f") val f: Boolean,
                @param:Name("nf") val nf: String,
                @param:Name("ba") val ba: ByteArray,
                @param:Name("wba") val wba: WrappedByteArray,
                @param:Name("noba") val noba: String,
                @param:Name("s") val s: String,
                @param:Name("n") @param:Nullable val n: String?,
                @param:Name("nn") val nn: String,
                @param:Name("gtv") val gtv: Gtv,
                @param:Name("li") val li: List<Long>,
                @param:Name("se") val se: Set<String>,
                @param:Name("ma") val ma: Map<String, Gtv>,
                @param:Name("def1") @param:DefaultValue(defaultString = "default1") val def1: String,
                @param:Name("def2") @param:DefaultValue(defaultString = "default2") val def2: String
        )

        val actual = GtvObjectMapper.fromGtv(GtvYaml().load(allPrimitivesYaml), AllPrimitives::class)
        assertThat(actual.l).isEqualTo(2L)
        assertThat(actual.nol).isEqualTo("17")
        assertThat(actual.bi).isEqualTo(BigInteger.valueOf(1234L))
        assertThat(actual.nobi).isEqualTo("1234L")
        assertThat(actual.t).isEqualTo(true)
        assertThat(actual.nt).isEqualTo("true")
        assertThat(actual.f).isEqualTo(false)
        assertThat(actual.nf).isEqualTo("false")
        assertThat(actual.ba).isContentEqualTo("12".hexStringToByteArray())
        assertThat(actual.wba).isEqualTo("13".hexStringToWrappedByteArray())
        assertThat(actual.noba).isEqualTo("x\"1234ABCD\"")
        assertThat(actual.s).isEqualTo("foo")
        assertThat(actual.n).isNull()
        assertThat(actual.nn).isEqualTo("null")
        assertThat(actual.gtv).isEqualTo(gtv(12))
        assertThat(actual.li).isEqualTo(listOf(1L, 2L))
        assertThat(actual.se).isEqualTo(setOf("a", "b"))
        assertThat(actual.ma).isEqualTo(mapOf("k1" to gtv("v1"), "k2" to gtv(5)))
        assertThat(actual.def1).isEqualTo("overridden")
        assertThat(actual.def2).isEqualTo("default2")
    }

    @Test
    fun allPrimitivesGtvTest() {
        data class AllPrimitivesGtv(
                @param:Name("l") val l: Gtv,
                @param:Name("nol") val nol: Gtv,
                @param:Name("bi") val bi: Gtv,
                @param:Name("nobi") val nobi: Gtv,
                @param:Name("t") val t: Gtv,
                @param:Name("nt") val nt: Gtv,
                @param:Name("f") val f: Gtv,
                @param:Name("nf") val nf: Gtv,
                @param:Name("ba") val ba: Gtv,
                @param:Name("wba") val wba: Gtv,
                @param:Name("s") val s: Gtv,
                @param:Name("n") val n: Gtv,
                @param:Name("nn") val nn: Gtv,
                @param:Name("gtv") val gtv: Gtv,
                @param:Name("ma") val ma: Map<String, Gtv>
        )

        val actual = GtvObjectMapper.fromGtv(GtvYaml().load(allPrimitivesYaml), AllPrimitivesGtv::class)
        assertThat(actual.l).isEqualTo(GtvInteger(2))
        assertThat(actual.nol).isEqualTo(GtvString("17"))
        assertThat(actual.bi).isEqualTo(GtvBigInteger(BigInteger.valueOf(1234L)))
        assertThat(actual.nobi).isEqualTo(GtvString("1234L"))
        assertThat(actual.t).isEqualTo(GtvInteger(1))
        assertThat(actual.nt).isEqualTo(GtvString("true"))
        assertThat(actual.f).isEqualTo(GtvInteger(0))
        assertThat(actual.nf).isEqualTo(GtvString("false"))
        assertThat(actual.ba).isEqualTo(GtvByteArray("12".hexStringToByteArray()))
        assertThat(actual.wba).isEqualTo(GtvByteArray("13".hexStringToByteArray()))
        assertThat(actual.s).isEqualTo(GtvString("foo"))
        assertThat(actual.n).isEqualTo(GtvNull)
        assertThat(actual.nn).isEqualTo(GtvString("null"))
        assertThat(actual.gtv).isEqualTo(gtv(12))
        assertThat(actual.ma).isEqualTo(mapOf("k1" to gtv("v1"), "k2" to gtv(5)))
    }

    @Test
    fun allGtvTest() {
        val expectedYaml = """
            ---
            ba: x"12"
            bi: 1234L
            bo: 1
            gtv: 12
            i: 1
            l: 200000000000000000000000000000000L
            li:
            - 1
            - 2
            ma:
              k1: v1
              k2: 5
            noba: 'x"1234ABCD"'
            nobi: '17L'
            s: foo
            se:
            - a
            - b
            wba: x"13"
            
        """.trimIndent()
        val gtvData = GtvYaml().load(expectedYaml)
        val actual = GtvYaml().dump(gtvData)
        assertThat(actual).isEqualTo(expectedYaml)
    }

    @Test
    fun gtvRoundTrip() {
        val expectedGtv = gtv(mapOf(
                "int" to gtv(17),
                "no_int" to gtv("17"),
                "bigint" to gtv(BigInteger("170000000000000000000000000000000000000")),
                "no_bigint" to gtv("17L"),
                "string" to gtv("foo bar"),
                "null" to GtvNull,
                "no_null" to gtv("null"),
                "bytearray" to gtv(byteArrayOf(1, 2, 3, 4)),
                "no_bytearray" to gtv("x\"1234ABCD\""),
                "array" to gtv(gtv(1), gtv(2), gtv(3)),
        ))
        val yaml = GtvYaml().dump(expectedGtv)
        val actual = GtvYaml().load(yaml)
        assertThat(actual).isEqualTo(expectedGtv)
    }

    @Disabled // this doesn't work properly
    @Test
    fun gtvControlCharInString() {
        val expectedGtv = gtv("\u0019Ethereum Signed Message:\n")
        val yaml = GtvYaml().dump(expectedGtv)
        val actual = GtvYaml().load(yaml)
        assertThat(actual).isEqualTo(expectedGtv)
    }

    companion object {
        @JvmStatic
        fun allGtvTypes() = arrayOf(
                arrayOf("1", gtv(1)),
                arrayOf("1000000000000000000", gtv(1000000000000000000)),
                arrayOf("10000000000000000000", gtv(BigInteger("10000000000000000000"))),
                arrayOf("1234L", gtv(BigInteger("1234"))),
                arrayOf("\"1234L\"", gtv("1234L")),
                arrayOf("true", gtv(true)),
                arrayOf("test", gtv("test")),
                arrayOf("1.2", gtv("1.2")),
                arrayOf("x\"AB\"", gtv("AB".hexStringToByteArray())),
                arrayOf("\n  - 1 \n  - 2", gtv(gtv(1), gtv(2))),
                arrayOf("\n  a: 37", gtv(mapOf("a" to gtv(37)))),
                arrayOf("null", GtvNull),
        )
    }
}
