package net.postchain.gtv

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Disabled
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

internal class GtvConverterTest {

    @Test
    fun testBasic() {
        val foo = gtv(mapOf("bar" to gtv(123L)))
        assert(GtvConverter.fromGtv(foo, BasicWithAnnotation::class)).isEqualTo(BasicWithAnnotation(123))
        assertThrows<Exception> { (GtvConverter.fromGtv(foo, BasicWithoutAnnotation::class)) }
        assert(GtvConverter.fromGtv(gtv(mapOf()), WithNullable::class)).isEqualTo(WithNullable(null))
    }

    @Test
    fun testAllPrimitiveTypes() {
        val g = gtv(mapOf(
                "s" to gtv("a"),
                "l" to gtv(1),
                "b" to gtv("b".toByteArray())
        ))
        val actual = GtvConverter.fromGtv(g, AllTypes::class)
        assert(actual.l).isEqualTo(1L)
        assert(actual.s).isEqualTo("a")
        assert(actual.b).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun testListType() {
        val g = gtv(mapOf("list" to gtv(gtv(1))))
        val actual = GtvConverter.fromGtv(g, BasicWithList::class)
        assert(actual.l).containsExactly(1L)
    }

    @Test
    fun testDict() {
        val g = gtv(mapOf("dict" to gtv(mapOf("bar" to gtv(1)))))
        val inner = BasicWithAnnotation(1)
        val outer = BasicDict(inner)
        assert(GtvConverter.fromGtv(g, BasicDict::class)).isEqualTo(outer)
    }

    @Test
    fun listTypes() {
        val g = gtv(mapOf("str" to gtv(gtv("a".toByteArray()), gtv("b".toByteArray()))))
        val actual = GtvConverter.fromGtv(g, ComplexList::class).stringList
        assert(actual[0]).isContentEqualTo("a".toByteArray())
        assert(actual[1]).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun listDict() {
        val g = gtv(mapOf("dict" to gtv(gtv(mapOf("bar" to gtv(1))))))
        val actual = GtvConverter.fromGtv(g, ListDict::class)
        assert(actual.dict).containsExactly(BasicWithAnnotation(1L))
    }

    @Test
    fun listList() {
        val g = gtv(mapOf("listlist" to gtv(gtv(gtv(mapOf("bar" to gtv(1L)))))))
        val actual = GtvConverter.fromGtv(g, ListList::class)
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
        assert(GtvConverter.fromGtv(g, Raw::class)).isEqualTo(Raw(g, 1))

        data class NestedRaw(@RawGtv val raw: Gtv, @Name("nested") val nested: Raw)
        val nested = gtv(mapOf("nested" to  gtv("a" to gtv(1))))
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
    @Disabled
    fun nonWorking() {
        // Nested lists on top level
        val g3 = gtv(gtv(gtv(1)))
        assert(g3.toList<List<Long>>()).containsExactly(listOf(1L))
    }
}

