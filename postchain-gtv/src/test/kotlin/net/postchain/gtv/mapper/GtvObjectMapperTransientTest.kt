package net.postchain.gtv.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClassWithTransientField(@param:Transient("transientValue") val longValue: Long)
class ClassWithNullableTransientField(@param:Transient("transientValue") @param:Nullable val longValue: Long?)

class GtvObjectMapperTransientTest {

    @Test
    fun transientField() {
        assertThat(gtv(mapOf()).toObject<ClassWithTransientField>(mapOf("transientValue" to 1)).longValue).isEqualTo(1L)
        assertThrows<IllegalArgumentException> {
            gtv(mapOf()).toObject<ClassWithTransientField>()
        }
    }

    @Test
    fun transientNullable() {
        assertThat(gtv(mapOf()).toObject<ClassWithNullableTransientField>().longValue).isNull()
    }

    @Test
    fun transientListField() {
        val gtvArray = gtv(gtv(mapOf()))
        assertThat(gtvArray.toList<ClassWithTransientField>(mapOf("transientValue" to 1))[0].longValue).isEqualTo(1L)

    }

    @Test
    fun transientNested() {
        class ClassWithTransientInnerField(
                @param:Name("b") val b: String,
                @param:Name("a") val a: ClassWithTransientField
        )
        val gtv = gtv(mapOf(
                "a" to gtv(mapOf()),
                "b" to gtv(""))
        )
        assertThrows<IllegalArgumentException> {
            gtv.toObject<ClassWithTransientInnerField>()
        }
        assertThat(gtv.toObject<ClassWithTransientInnerField>(mapOf("transientValue" to 1L)).a.longValue).isEqualTo(1L)
        assertThat(gtv(gtv).toList<ClassWithTransientInnerField>(mapOf("transientValue" to 1L))[0].a.longValue).isEqualTo(1L)
    }
}
