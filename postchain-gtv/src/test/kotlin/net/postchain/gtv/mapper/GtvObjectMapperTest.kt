package net.postchain.gtv.mapper

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.isContentEqualTo
import net.postchain.common.BlockchainRid
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.BigInteger

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
        assertThat(GtvObjectMapper.fromGtv(dummy, Simple::class)).isEqualTo(Simple(123))
    }

    @Test
    fun fromGtv() {
        val dummy = gtv(mapOf("foo" to gtv("FOO"), "bar" to gtv("<<<BAR>>>")))
        assertThat(GtvObjectMapper.fromGtv(dummy, WithCustom::class)).isEqualTo(WithCustom("FOO", Custom("BAR")))
    }

    @Test
    fun fromGtvIntIsNotAccepted() {
        assertThrows<IllegalArgumentException> {
            GtvObjectMapper.fromGtv(gtv(mapOf("key" to gtv(17L))), IntField::class)
        }
    }

    @Test
    fun nullablePropertyIsNull() {
        data class SimpleNullable(@Name("missing") @Nullable val foo: Long?)
        assertThat(GtvObjectMapper.fromGtv(gtv(mapOf()), SimpleNullable::class)).isEqualTo(SimpleNullable(null))
        assertThat(GtvObjectMapper.fromGtv(gtv(mapOf("missing" to GtvNull)), SimpleNullable::class)).isEqualTo(SimpleNullable(null))
    }

    @Test
    fun invalidNullableUsage() {
        data class SimpleNullable(@Name("missing") @Nullable val foo: Long)

        val e = assertThrows<IllegalArgumentException> {
            GtvObjectMapper.fromGtv(gtv(mapOf()), SimpleNullable::class)
        }
        assertThat(e.message).isEqualTo("Constructor for ${SimpleNullable::class.simpleName} with parameters [null] not found")
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
                @Name("enum") val e: SimpleEnum,
                @Name("byte") val b: ByteArray, // Do not run isEqualTo
                @Name("wbyte") val w: WrappedByteArray,
                @Name("row") val r: RowId,
                @Name("pubkey") val pk: PubKey,
                @Name("blockchain_rid") val brid: BlockchainRid,
        )

        val actual = gtv(mapOf(
                "string" to gtv("a"),
                "long" to gtv(1),
                "enum" to gtv("A"),
                "byte" to gtv("b".toByteArray()),
                "wbyte" to gtv("w".toByteArray()),
                "row" to gtv(RowId(17).id),
                "pubkey" to gtv(ByteArray(33)),
                "blockchain_rid" to gtv(ByteArray(32))
        )).toObject<AllTypes>()
        assertThat(actual.l).isEqualTo(1L)
        assertThat(actual.s).isEqualTo("a")
        assertThat(actual.e).isEqualTo(SimpleEnum.A)
        assertThat(actual.b).isContentEqualTo("b".toByteArray())
        assertThat(actual.w).isEqualTo(WrappedByteArray("w".toByteArray()))
        assertThat(actual.r).isEqualTo(RowId(17))
        assertThat(actual.pk).isEqualTo(PubKey(ByteArray(33)))
        assertThat(actual.brid).isEqualTo(BlockchainRid.ZERO_RID)
    }

    @Test
    fun testMissingEnumValue() {
        data class MissingEnumValue(
                @Name("enum") val e: SimpleEnum,
        )

        assertFailure {
            gtv(mapOf(
                    "enum" to gtv("C")
            )).toObject<MissingEnumValue>()
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun bigIntegerType() {
        data class SimpleBigInteger(@Name("myBigInt") @DefaultValue(defaultBigInteger = "15") val myBigInteger: BigInteger)
        assertThat(gtv(mapOf("myBigInt" to gtv(BigInteger("9999209385237856329573295739345354354354353")))).toObject<SimpleBigInteger>())
                .isEqualTo(SimpleBigInteger(BigInteger("9999209385237856329573295739345354354354353")))
        assertThat(gtv(mapOf()).toObject<SimpleBigInteger>()).isEqualTo(SimpleBigInteger(BigInteger("15")))
    }

    @Test
    fun booleanType() {
        data class SimpleBoolean(@Name("myBoolean") @DefaultValue(defaultBoolean = false) val myBoolean: Boolean)
        assertThat(gtv(mapOf("myBoolean" to gtv(1L))).toObject<SimpleBoolean>()).isEqualTo(SimpleBoolean(true))
        assertThat(gtv(mapOf()).toObject<SimpleBoolean>()).isEqualTo(SimpleBoolean(false))
    }

    @Test
    fun decimalType() {
        data class SimpleDecimal(@Name("myDecimal") @DefaultValue(defaultDecimal = "12.34") val myDecimal: BigDecimal)
        assertThat(gtv(mapOf("myDecimal" to gtv("23.45"))).toObject<SimpleDecimal>())
                .isEqualTo(SimpleDecimal(BigDecimal("23.45")))
        assertThat(gtv(mapOf()).toObject<SimpleDecimal>()).isEqualTo(SimpleDecimal(BigDecimal("12.34")))
    }

    @Test
    fun testCollectionTypes() {
        data class BasicWithList(@Name("list") val l: List<Long>) // Do not run isEqualTo
        data class BasicWithSet(@Name("list") val l: Set<Long>) // Do not run isEqualTo
        data class BasicWithCollection(@Name("list") val l: Collection<Long>) // Do not run isEqualTo

        assertThat(gtv(mapOf("list" to gtv(gtv(1))))
                .toObject<BasicWithList>().l).containsExactly(1L)

        assertThat(gtv(mapOf("list" to gtv(gtv(1))))
                .toObject<BasicWithCollection>().l).containsAll(1L)

        assertThat(gtv(mapOf("list" to gtv(gtv(1))))
                .toObject<BasicWithSet>().l).containsAll(1L)
    }

    @Test
    fun testDict() {
        val inner = Simple(1)
        val outer = BasicDict(inner)
        val innerGtv = gtv(mapOf("key" to gtv(inner.value)))
        val actual = gtv(mapOf("dict" to innerGtv))
                .toObject<BasicDict>()
        assertThat(actual).isEqualTo(outer)
    }

    @Test
    fun listTypes() {
        data class ComplexList(@Name("str") val stringList: List<ByteArray>) // Do not run isEqualTo

        val g = gtv(mapOf("str" to gtv(gtv("a".toByteArray()), gtv("b".toByteArray()))))
        val actual = GtvObjectMapper.fromGtv(g, ComplexList::class).stringList
        assertThat(actual[0]).isContentEqualTo("a".toByteArray())
        assertThat(actual[1]).isContentEqualTo("b".toByteArray())
    }

    @Test
    fun listDict() {
        data class ListDict(@Name("dict") val dict: List<Simple>) // Do not run isEqualTo

        val g = gtv(mapOf("dict" to gtv(gtv(mapOf("key" to gtv(1))))))
        val actual = GtvObjectMapper.fromGtv(g, ListDict::class)
        assertThat(actual.dict).containsExactly(Simple(1L))
    }

    @Test
    fun listList() {
        data class ListOfList(@Name("listlist") val listOfList: List<List<Simple>>) // Do not run isEqualTo

        val simple = gtv(mapOf("key" to gtv(1L)))
        val listOfList = gtv(gtv(simple))
        val actual = gtv(mapOf("listlist" to listOfList)).toObject<ListOfList>()
        assertThat(actual.listOfList).containsExactly(listOf(Simple(1L)))
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
        assertThat(actual).isEqualTo(NestedDict(BasicDict(Simple(1))))
    }

    @Test
    fun listOfClass() {
        val listofSimple = gtv(gtv(mapOf("key" to gtv(1))))
                .toList<Simple>()

        assertThat(listofSimple).containsExactly(Simple(1))
    }

    @Test
    fun listOfPrimitive() {
        val listOfLong = gtv(gtv(1)).toList<Long>()
        assertThat(listOfLong).containsExactly(1L)
    }

    @Test
    fun saveRawData() {
        data class WithRawData(@RawGtv val raw: Gtv, @Name("a") val dummy: Long)

        val rawGtv = gtv("a" to gtv(1))
        assertThat(rawGtv.toObject<WithRawData>()).isEqualTo(WithRawData(rawGtv, 1))

    }

    @Test
    fun storeAsUnconverted() {
        data class UnConverted(@Name("asGtv") val g: Gtv)

        val actual = gtv(mapOf("asGtv" to gtv(1))).toObject<UnConverted>()
        assertThat(actual).isEqualTo(UnConverted(gtv(1)))
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
        assertThat(actual.b).isContentEqualTo(byteArrayOf(0x2E))
        assertThat(actual.l).isEqualTo(5L)
        assertThat(actual.s).isEqualTo("foo")
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

        assertThat(a.toObject<A>()).isEqualTo(A("foo", bDict, B("foo", 1), a))
    }

    @Test
    fun assignGtvTypes() {
        data class SimpleDict(@Name("dict") val dict: GtvDictionary)
        val a = gtv(mapOf(
                "dict" to gtv(mapOf())
        ))
        assertThat(a.toObject<SimpleDict>()).isEqualTo(SimpleDict(gtv(mapOf())))
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
        assertThat(e.message).isEqualTo("Expected path b to be GtvDictionary")
    }

    @Test
    fun nestedMissingPath() {
        data class C(
                @Name("name")
                @Nested("b")
                @Nullable
                val name: String?
        )
        assertThat(gtv(mapOf()).toObject<C>().name).isNull()
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
        assertThat(g.toObject<Multi>()).isEqualTo(Multi("foo"))
    }

    @Test
    fun genericTypesWillThrow() {
        assertThrows<IllegalArgumentException> { gtv(mapOf("a" to gtv(1))).toObject<Map<String, Gtv>>() }
        assertThrows<IllegalArgumentException> { gtv(gtv(gtv(1))).toList<List<Long>>() }
    }

    @Test
    fun unknownMap() {
        data class ValidMapType(@Name("foo") val map: Map<String, Simple>)

        val g = gtv(mapOf(
                "foo" to gtv(mapOf(
                        "any" to gtv(mapOf("key" to gtv(1)))
                ))
        ))

        assertThat(g.toObject<ValidMapType>().map["any"]?.value).isEqualTo(1L)

        data class WrongMapType(@Name("foo") val map: Map<Int, Simple>)
        assertThrows<IllegalArgumentException> {
            g.toObject<WrongMapType>()
        }

    }

    @Test
    fun javaClass() {
        val dummy = gtv(mapOf("value" to gtv("FOO")))
        assertThat(GtvObjectMapper.fromGtv(dummy, AJavaClass::class)).isEqualTo(AJavaClass("FOO"))
    }

    @Test
    fun unsupportedType() {
        val e = assertThrows<IllegalArgumentException> {
            GtvObjectMapper.fromGtv(gtv(mapOf("foo" to gtv(17L))), UnsupportedConstructorParamType::class)
        }
        assertThat(e.message).isEqualTo("Gtv must be a dictionary, but is: INTEGER with values 17; context: foo")
    }
}
