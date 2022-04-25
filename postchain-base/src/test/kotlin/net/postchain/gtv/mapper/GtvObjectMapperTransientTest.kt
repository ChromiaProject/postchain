package net.postchain.gtv.mapper

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClassWithTransientField(@Transient("transientValue") val longValue: Long)
class ClassWithNullableTransientField(@Transient("transientValue") @Nullable val longValue: Long?)

class GtvObjectMapperTransientTest {

    @Test
    fun transientField() {
        assert(gtv(mapOf()).toObject<ClassWithTransientField>(mapOf("transientValue" to 1)).longValue).isEqualTo(1L)
        assertThrows<IllegalArgumentException> {
            gtv(mapOf()).toObject<ClassWithTransientField>()
        }
    }

    @Test
    fun transientNullable() {
        assert(gtv(mapOf()).toObject<ClassWithNullableTransientField>().longValue).isNull()
    }

    @Test
    fun transientListField() {
        val gtvArray = gtv(gtv(mapOf()))
        assert(gtvArray.toList<ClassWithTransientField>(mapOf("transientValue" to 1))[0].longValue).isEqualTo(1L)

    }

    @Test
    fun transientNested() {
        class ClassWithTransientInnerField(
                @Name("b") val b: String,
                @Name("a") val a: ClassWithTransientField
        )
        val gtv = gtv(mapOf(
                "a" to gtv(mapOf()),
                "b" to gtv(""))
        )
        assertThrows<IllegalArgumentException> {
            gtv.toObject<ClassWithTransientInnerField>()
        }
        assert(gtv.toObject<ClassWithTransientInnerField>(mapOf("transientValue" to 1L)).a.longValue).isEqualTo(1L)
        assert(gtv(gtv).toList<ClassWithTransientInnerField>(mapOf("transientValue" to 1L))[0].a.longValue).isEqualTo(1L)
    }
}
