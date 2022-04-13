package net.postchain.gtv.mapper

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

data class BasicWithAnnotation(@Name("bar") val bar: Long)
data class BasicWithoutAnnotation(val bar: Long)
data class WithNullable(@Name("foo") @Nullable val foo: Long?)
data class AllTypes(@Name("s") val s: String, @Name("l") val l: Long, @Name("b") val b: ByteArray) // Do not run isEqualTo

data class BasicWithList(@Name("list") val l: List<Long>) // Do not run isEqualTo
data class ComplexList(@Name("str") val stringList: List<ByteArray>) // Do not run isEqualTo
data class ListDict(@Name("dict") val dict: List<BasicWithAnnotation>) // Do not run isEqualTo
data class ListList(@Name("listlist") val listlist: List<List<BasicWithAnnotation>>) // Do not run isEqualTo

data class BasicDict(@Name("dict") val basicWithAnnotation: BasicWithAnnotation)

data class NestedDict(@Name("nestedDict") val nested: BasicDict)

internal class GtvObjectMapperTest {

    @Test
    fun testBasic() {
        val foo = gtv(mapOf("bar" to gtv(123L)))
        assert(GtvObjectMapper.fromGtv(foo, BasicWithAnnotation::class)).isEqualTo(BasicWithAnnotation(123))
        assertThrows<Exception> { (GtvObjectMapper.fromGtv(foo, BasicWithoutAnnotation::class)) }
        assert(GtvObjectMapper.fromGtv(gtv(mapOf()), WithNullable::class)).isEqualTo(WithNullable(null))
    }

    @Test
    fun testAllPrimitiveTypes() {
        val g = gtv(mapOf(
                "s" to gtv("a"),
                "l" to gtv(1),
                "b" to gtv("b".toByteArray())
        ))
        val actual = GtvObjectMapper.fromGtv(g, AllTypes::class)
        assert(actual.l).isEqualTo(1L)
        assert(actual.s).isEqualTo("a")
        assert(actual.b).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun testListType() {
        val g = gtv(mapOf("list" to gtv(gtv(1))))
        val actual = GtvObjectMapper.fromGtv(g, BasicWithList::class)
        assert(actual.l).containsExactly(1L)
    }

    @Test
    fun testDict() {
        val g = gtv(mapOf("dict" to gtv(mapOf("bar" to gtv(1)))))
        val inner = BasicWithAnnotation(1)
        val outer = BasicDict(inner)
        assert(GtvObjectMapper.fromGtv(g, BasicDict::class)).isEqualTo(outer)
    }

    @Test
    fun listTypes() {
        val g = gtv(mapOf("str" to gtv(gtv("a".toByteArray()), gtv("b".toByteArray()))))
        val actual = GtvObjectMapper.fromGtv(g, ComplexList::class).stringList
        assert(actual[0]).isContentEqualTo("a".toByteArray())
        assert(actual[1]).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun listDict() {
        val g = gtv(mapOf("dict" to gtv(gtv(mapOf("bar" to gtv(1))))))
        val actual = GtvObjectMapper.fromGtv(g, ListDict::class)
        assert(actual.dict).containsExactly(BasicWithAnnotation(1L))
    }

    @Test
    fun listList() {
        val g = gtv(mapOf("listlist" to gtv(gtv(gtv(mapOf("bar" to gtv(1L)))))))
        val actual = GtvObjectMapper.fromGtv(g, ListList::class)
        assert(actual.listlist).containsExactly(listOf(BasicWithAnnotation(1L)))
    }

    @Test
    fun nestedDict() {
        val g = gtv(mapOf(
                "nestedDict" to gtv(mapOf(
                        "dict" to gtv(mapOf(
                                "bar" to gtv(1)
                        ))
                ))))
        assert(g.toClass<NestedDict>()).isEqualTo(NestedDict(BasicDict(BasicWithAnnotation(1))))
    }

    @Test
    fun listOfClass() {
        val g = gtv(gtv(mapOf("bar" to gtv(1))))

        assert(g.toList<BasicWithAnnotation>()).containsExactly(BasicWithAnnotation(1))

        val g2 = gtv(gtv(1))
        assert(g2.toList<Long>()).containsExactly(1L)
    }

    @Test
    fun saveRawData() {
        // save "raw" as a separate tag
        data class Raw(@RawGtv val raw: Gtv, @Name("a") val dummy: Long)

        val g = gtv("a" to gtv(1))
        assert(GtvObjectMapper.fromGtv(g, Raw::class)).isEqualTo(Raw(g, 1))

        data class NestedRaw(@RawGtv val raw: Gtv, @Name("nested") val nested: Raw)

        val nested = gtv(mapOf("nested" to gtv("a" to gtv(1))))
        assert(nested.toClass<NestedRaw>()).isEqualTo(NestedRaw(nested, Raw(g, 1)))

        data class UnConverted(@Name("asgtv") val g: Gtv)
        assert(gtv(mapOf("asgtv" to gtv(1))).toClass<UnConverted>()).isEqualTo(UnConverted(gtv(1)))
    }

    @Test
    fun defaultValue() {
        data class WithDefaultValue(
                @Name("defaultLong") @DefaultValue(defaultLong = 5L) val l: Long,
                @Name("defaultString") @DefaultValue(defaultString = "foo") val s: String,
                @Name("defaultByteArray") @DefaultValue(defaultByteArray = [0x2E]) val b: ByteArray,
        )

        val def = gtv(mapOf())
        val actual = def.toClass<WithDefaultValue>()
        assert(actual.b).isContentEqualTo(byteArrayOf(0x2E))
        assert(actual.l).isEqualTo(5L)
        assert(actual.s).isEqualTo("foo")
    }

    @Test
    fun withoutAnnotationThrows() {
        data class WithoutAnnotation(val foo: Long)
        assertThrows<IllegalArgumentException> { gtv(mapOf("foo" to gtv(1))).toClass<WithoutAnnotation>() }
    }

    @Test
    fun missingGtvThrows() {
        assertThrows<IllegalArgumentException> { gtv(mapOf()).toClass<BasicWithAnnotation>() }
    }

    @Test
    fun defaultValueIsNotPrimitive() {
        data class NonPrimitiveDefault(
                @Name("foo") @DefaultValue val foo: BasicWithAnnotation
        )

        assertThrows<IllegalArgumentException> {
            gtv(mapOf()).toClass<NonPrimitiveDefault>()
        }
    }

    @Test
    fun explicitPath() {
        data class B(
                @Name("name") val name: String,
                @Name("value") val value: Long
        )

        data class A(
                @Name("name")
                @Nested("b")
                val bName: String,
                @Name("b") val bRaw: Gtv,
                @Name("b") val b: B,
                @RawGtv val raw: Gtv
        )
        val bDict = gtv(mapOf(
                "name" to gtv("foo"),
                "value" to gtv(1)
        ))
        val a = gtv(mapOf(
                "b" to bDict
        ))

        assert(a.toClass<A>()).isEqualTo(A("foo", bDict, B("foo", 1), a))
    }

    @Test
    fun invalidPath() {
        data class C(
                @Name("name")
                @Nested("b")
                val name: String
        )
        val list = gtv(gtv(gtv("foo")))
        val e = assertThrows<IllegalArgumentException> {
            gtv(mapOf("b" to list)).toClass<C>()
        }
        assert(e.message).isEqualTo("Expected path b to be GtvDictionary")
    }

    @Test
    fun multiLevelPath() {
        data class Multi(
                @Name("name")
                @Nested("a", "b", "c")
                val name: String
        )
        val g = gtv(mapOf(
                "a" to gtv(mapOf(
                        "b" to gtv(mapOf(
                                "c" to gtv(mapOf(
                                        "name" to gtv("foo")
                                ))
                        ))
                ))
        ))
        assert(g.toClass<Multi>()).isEqualTo(Multi("foo"))
    }

    @Test
    fun genericTypesWillThrow() {
        assertThrows<IllegalArgumentException> { gtv(mapOf("a" to gtv(1))).toClass<Map<String, Gtv>>() }
        assertThrows<IllegalArgumentException> { gtv(gtv(gtv(1))).toList<List<Long>>() }
    }

}

