package net.postchain.gtv.yaml

import assertk.assert
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

    @Test
    fun customTest() {

        class AllPrimitives {
            var i: Int? = null
            var l: Long? = null
            var bo: Boolean? = null
            var ba1: ByteArray? = null
            var ba2: ByteArray? = null
            var wba1: WrappedByteArray? = null
            var wba2: WrappedByteArray? = null
            var s: String? = null
            var gtv: Gtv? = null
            var li: List<Long>? = null
            var se: Set<String>? = null
            var ma: Gtv? = null
            var def1: String = "default1"
            var def2: String = "default2"
        }
        val actual = GtvYaml(AllPrimitives::class.java).load<AllPrimitives>("""
            i: 1
            l: 2
            bo: true
            ba1: !!binary AB
            ba2: 0x12
            wba1: !!binary A0
            wba2: 0x13
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
        assert(actual.i).isEqualTo(1)
        assert(actual.l).isEqualTo(2L)
        assert(actual.bo).isEqualTo(true)
        assertContentEquals("AB".hexStringToByteArray(), actual.ba1)
        assertContentEquals("12".hexStringToByteArray(), actual.ba2)
        assert(actual.wba1).isEqualTo("A0".hexStringToWrappedByteArray())
        assert(actual.wba2).isEqualTo("13".hexStringToWrappedByteArray())
        assert(actual.s).isEqualTo("foo")
        assert(actual.gtv).isEqualTo(gtv(12))
        assert(actual.li).isEqualTo(listOf(1L, 2L))
        assert(actual.se).isEqualTo(setOf("a", "b"))
        assert(actual.ma).isEqualTo(mapOf("k1" to gtv("v1"), "k2" to gtv(5)))
        assert(actual.def1).isEqualTo("overridden")
        assert(actual.def2).isEqualTo("default2")
        class A {
            var v: Long? = null
        }

        val a = GtvYaml(A::class.java).load<A>("v: 1")
        assert(a.v).isEqualTo(1L)
        class B {
            var v: ByteArray? = null
        }

        val b = GtvYaml(B::class.java).load<B>("v: 0xAB")
        assertContentEquals("AB".hexStringToByteArray(), b.v)
        val b2 = GtvYaml(B::class.java).load<B>("v: !!binary AB")
        assertContentEquals("AB".hexStringToByteArray(), b2.v)

        class C {
            var v: Gtv? = null
        }

        val c = GtvYaml(C::class.java).load<C>("v: test")
        assert(c.v).isEqualTo(gtv("test"))

        class D {
            var a: String? = null
            var v: String? = "default"
        }

        val d = GtvYaml(D::class.java).load<D>("a: d")
        assert(d.v).isEqualTo("default")

        val d2 = GtvYaml(D::class.java).load<D>("""
            a: d
            v: new
        """.trimIndent())
        assert(d2.v).isEqualTo("new")

        class E {
            var v: WrappedByteArray? = null
        }
        val e = GtvYaml(E::class.java).load<E>("v: !!binary AB")
        assert(e.v).isEqualTo("AB".hexStringToWrappedByteArray())
        val e2 = GtvYaml(E::class.java).load<E>("v: 0xAB")
        assert(e2.v).isEqualTo("AB".hexStringToWrappedByteArray())


        class F {
            var l: List<String>? = null
        }
        val f = GtvYaml(F::class.java).load<F>("""
          l:
            - a
            - b
        """.trimIndent())

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
