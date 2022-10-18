package net.postchain.gtv.mapper

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class ObjectToGtvArrayTest {

    @Test
    fun simpleToArray() {
        assert(GtvObjectMapper.toGtvArray(Simple(1L))).isEqualTo(gtv(gtv(1)))
    }

    @Test
    fun illegalAnnotations() {
        data class RawGtvClass(@RawGtv val gtv: Gtv)
        assertThrows<IllegalArgumentException> { GtvObjectMapper.toGtvArray(RawGtvClass(gtv(1))) }
        data class NestedClass(@Nested val nestedValue: Long)
        assertThrows<IllegalArgumentException> { GtvObjectMapper.toGtvArray(NestedClass(1)) }
        data class TransientClass(@Transient("foo") val transientValue: Long)
        assertThrows<IllegalArgumentException> { GtvObjectMapper.toGtvArray(TransientClass(1)) }
    }

    @Test
    fun simpleTypes() {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        data class AllPrimitives(
                val long: Long = 1,
                val javaLong: java.lang.Long = java.lang.Long.valueOf(2) as java.lang.Long,
                val string: String = "foo",
                val bool: Boolean = true,
                val byteArray: ByteArray = ByteArray(32),
                val enum: SimpleEnum = SimpleEnum.A,
                val bigInteger: BigInteger = BigInteger.ONE,
                val wrappedByteArray: WrappedByteArray = WrappedByteArray(16),
                val rowId: RowId = RowId(17)
        )
        assert(GtvObjectMapper.toGtvArray(AllPrimitives()).array).isContentEqualTo(listOf(
                gtv(1),
                gtv(2),
                gtv("foo"),
                gtv(true),
                gtv(ByteArray(32)),
                gtv("A"),
                gtv(BigInteger.ONE),
                gtv(WrappedByteArray(16)),
                gtv(RowId(17))
        ).toTypedArray())
    }

    @Test
    fun nullableType() {
        data class NullableField(val long: Long?)
        assert(GtvObjectMapper.toGtvArray(NullableField(null))).isEqualTo(gtv(GtvNull))
    }

    @Test
    fun nestedType() {
        data class NestedField(val simple: Simple)
        assert(GtvObjectMapper.toGtvArray(NestedField(Simple(1)))).isEqualTo(gtv(gtv(gtv(1))))
    }

    @Test
    fun collectionTypes() {
        data class CollectionFields(
                val coll: Collection<Long> = listOf(1),
                val list: List<String> = listOf("foo"),
                val set: Set<Simple> = setOf(Simple(2)),
                val nestedList: List<List<Boolean>> = listOf(listOf(true)),
        )
        assert(GtvObjectMapper.toGtvArray(CollectionFields())).isEqualTo(gtv(
                gtv(gtv(1)),
                gtv(gtv("foo")),
                gtv(gtv(gtv(2))), // Nested objects are mapped as arrays
                gtv(gtv(gtv(true)))
        ))
    }

    @Test
    fun listType() {
        assert(GtvObjectMapper.toGtvArray(listOf(1L))).isEqualTo(gtv(gtv(1)))
    }

    @Test
    fun mapType() {
        // [[k1, v1], [k2, v2], ...]
        assert(GtvObjectMapper.toGtvArray(mapOf("a" to 1L))).isEqualTo(gtv(gtv(gtv("a"), gtv(1))))
    }
}
