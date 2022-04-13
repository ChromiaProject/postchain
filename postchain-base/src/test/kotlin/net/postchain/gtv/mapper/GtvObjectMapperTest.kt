package net.postchain.gtv.mapper

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

data class Simple(@Name("key") val value: Long)

data class BasicDict(@Name("dict") val simple: Simple)

internal class GtvObjectMapperTest {

    @Test
    fun missingAnnotation() {
        data class SimpleWithoutAnnotation(val value: Long)
        assertThrows<Exception> {
            GtvObjectMapper.fromGtv(
                    gtv(mapOf("key" to gtv(1))), SimpleWithoutAnnotation::class
            )
        }
    }

    @Test
    fun annotationIsRespected() {
        val dummy = gtv(mapOf("key" to gtv(123L)))
        assert(GtvObjectMapper.fromGtv(dummy, Simple::class)).isEqualTo(Simple(123))
    }

    @Test
    fun nullablePropertyIsNull() {
        data class SimpleNullable(@Name("missing") @Nullable val foo: Long?)
        assert(GtvObjectMapper.fromGtv(gtv(mapOf()), SimpleNullable::class)).isEqualTo(SimpleNullable(null))
        assert(GtvObjectMapper.fromGtv(gtv(mapOf("missing" to GtvNull)), SimpleNullable::class)).isEqualTo(SimpleNullable(null))
    }

    @Test
    fun invalidNullableUsage() {
        data class SimpleNullable(@Name("missing") @Nullable val foo: Long)

        val e = assertThrows<IllegalArgumentException> {
            GtvObjectMapper.fromGtv(gtv(mapOf()), SimpleNullable::class)
        }
        assert(e.message).isEqualTo("Constructor for parameters [null] not found")
    }

    @Test
    fun missingGtvThrows() {
        assertThrows<IllegalArgumentException> {
            gtv(mapOf()).toObject<Simple>()
        }
    }

    @Test
    fun testAllPrimitiveTypes() {
        data class AllTypes(
                @Name("string") val s: String,
                @Name("long") val l: Long,
                @Name("byte") val b: ByteArray // Do not run isEqualTo
        )

        val actual = gtv(mapOf(
                "string" to gtv("a"),
                "long" to gtv(1),
                "byte" to gtv("b".toByteArray())
        )).toObject<AllTypes>()
        assert(actual.l).isEqualTo(1L)
        assert(actual.s).isEqualTo("a")
        assert(actual.b).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun bigIntegerType() {
        data class SimpleBigInteger(@Name("myBigInt") @DefaultValue(defaultBigInteger = "15") val myBigInteger: BigInteger)
        assert(gtv(mapOf("myBigInt" to gtv(BigInteger("9999209385237856329573295739345354354354353")))).toObject<SimpleBigInteger>())
                .isEqualTo(SimpleBigInteger(BigInteger("9999209385237856329573295739345354354354353")))
        assert(gtv(mapOf()).toObject<SimpleBigInteger>()).isEqualTo(SimpleBigInteger(BigInteger("15")))
    }

    @Test
    fun booleanType() {
        data class SimpleBoolean(@Name("myBoolean") @DefaultValue(defaultBoolean = false) val myBoolean: Boolean)
        assert(gtv(mapOf("myBoolean" to gtv(BigInteger("9999209385237856329573295739345354354354353")))).toObject<SimpleBoolean>()).isEqualTo(SimpleBoolean(true))
        assert(gtv(mapOf()).toObject<SimpleBoolean>()).isEqualTo(SimpleBoolean(false))
    }

    @Test
    fun testListType() {
        data class BasicWithList(@Name("list") val l: List<Long>) // Do not run isEqualTo

        val actual = gtv(mapOf("list" to gtv(gtv(1))))
                .toObject<BasicWithList>()
        assert(actual.l).containsExactly(1L)
    }

    @Test
    fun testDict() {
        val inner = Simple(1)
        val outer = BasicDict(inner)
        val innerGtv = gtv(mapOf("key" to gtv(inner.value)))
        val actual = gtv(mapOf("dict" to innerGtv))
                .toObject<BasicDict>()
        assert(actual).isEqualTo(outer)
    }

    @Test
    fun listTypes() {
        data class ComplexList(@Name("str") val stringList: List<ByteArray>) // Do not run isEqualTo

        val g = gtv(mapOf("str" to gtv(gtv("a".toByteArray()), gtv("b".toByteArray()))))
        val actual = GtvObjectMapper.fromGtv(g, ComplexList::class).stringList
        assert(actual[0]).isContentEqualTo("a".toByteArray())
        assert(actual[1]).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun listDict() {
        data class ListDict(@Name("dict") val dict: List<Simple>) // Do not run isEqualTo

        val g = gtv(mapOf("dict" to gtv(gtv(mapOf("key" to gtv(1))))))
        val actual = GtvObjectMapper.fromGtv(g, ListDict::class)
        assert(actual.dict).containsExactly(Simple(1L))
    }

    @Test
    fun listList() {
        data class ListOfList(@Name("listlist") val listOfList: List<List<Simple>>) // Do not run isEqualTo

        val simple = gtv(mapOf("key" to gtv(1L)))
        val listOfList = gtv(gtv(simple))
        val actual = gtv(mapOf("listlist" to listOfList)).toObject<ListOfList>()
        assert(actual.listOfList).containsExactly(listOf(Simple(1L)))
    }

    @Test
    fun nestedDict() {
        data class NestedDict(@Name("nestedDict") val nested: BasicDict)

        val actual = gtv(mapOf(
                "nestedDict" to gtv(mapOf(
                        "dict" to gtv(mapOf(
                                "key" to gtv(1)
                        ))
                )))).toObject<NestedDict>()
        assert(actual).isEqualTo(NestedDict(BasicDict(Simple(1))))
    }

    @Test
    fun listOfClass() {
        val listofSimple = gtv(gtv(mapOf("key" to gtv(1))))
                .toList<Simple>()

        assert(listofSimple).containsExactly(Simple(1))
    }

    @Test
    fun listOfPrimitive() {
        val listOfLong = gtv(gtv(1)).toList<Long>()
        assert(listOfLong).containsExactly(1L)
    }

    @Test
    fun saveRawData() {
        data class WithRawData(@RawGtv val raw: Gtv, @Name("a") val dummy: Long)

        val rawGtv = gtv("a" to gtv(1))
        assert(rawGtv.toObject<WithRawData>()).isEqualTo(WithRawData(rawGtv, 1))

    }

    @Test
    fun storeAsUnconverted() {
        data class UnConverted(@Name("asGtv") val g: Gtv)

        val actual = gtv(mapOf("asGtv" to gtv(1))).toObject<UnConverted>()
        assert(actual).isEqualTo(UnConverted(gtv(1)))
    }

    @Test
    fun defaultValue() {
        data class WithDefaultValue(
                @Name("defaultLong") @DefaultValue(defaultLong = 5L) val l: Long,
                @Name("defaultString") @DefaultValue(defaultString = "foo") val s: String,
                @Name("defaultByteArray") @DefaultValue(defaultByteArray = [0x2E]) val b: ByteArray,
        )

        val emptyGtv = gtv(mapOf())
        val actual = emptyGtv.toObject<WithDefaultValue>()
        assert(actual.b).isContentEqualTo(byteArrayOf(0x2E))
        assert(actual.l).isEqualTo(5L)
        assert(actual.s).isEqualTo("foo")
    }


    @Test
    fun defaultValueIsNotPrimitive() {
        data class NonPrimitiveDefault(
                @Name("foo") @DefaultValue val foo: Simple
        )

        assertThrows<IllegalArgumentException> {
            gtv(mapOf()).toObject<NonPrimitiveDefault>()
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

        assert(a.toObject<A>()).isEqualTo(A("foo", bDict, B("foo", 1), a))
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
            gtv(mapOf("b" to list)).toObject<C>()
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
        assert(g.toObject<Multi>()).isEqualTo(Multi("foo"))
    }

    @Test
    fun genericTypesWillThrow() {
        assertThrows<IllegalArgumentException> { gtv(mapOf("a" to gtv(1))).toObject<Map<String, Gtv>>() }
        assertThrows<IllegalArgumentException> { gtv(gtv(gtv(1))).toList<List<Long>>() }
    }
}
