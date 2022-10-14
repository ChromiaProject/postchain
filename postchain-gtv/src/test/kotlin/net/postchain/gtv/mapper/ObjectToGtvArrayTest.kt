package net.postchain.gtv.mapper

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
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
        data class AllPrimitives(
                val long: Long = 1,
                val string: String = "foo",
                val bool: Boolean = true,
                val byteArray: ByteArray = ByteArray(32),
                val enum: SimpleEnum = SimpleEnum.A,
                val bigInteger: BigInteger = BigInteger.ONE,
        )
        assert(GtvObjectMapper.toGtvArray(AllPrimitives()).array).isContentEqualTo(listOf(
                gtv(1),
                gtv("foo"),
                gtv(true),
                gtv(ByteArray(32)),
                gtv("A"),
                gtv(BigInteger.ONE)
        ).toTypedArray())

    }

}