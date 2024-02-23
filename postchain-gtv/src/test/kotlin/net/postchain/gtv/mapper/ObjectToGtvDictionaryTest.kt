package net.postchain.gtv.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ObjectToGtvDictionaryTest {

    @Test
    fun basicMap() {
        assertThat(GtvObjectMapper.toGtvDictionary(mapOf("foo" to 1L))).isEqualTo(gtv(mapOf("foo" to gtv(1))))
    }

    @Test
    fun toGtv() {
        assertThat(GtvObjectMapper.toGtvDictionary(WithCustom("FOO", Custom("BAR")))).isEqualTo(gtv(mapOf("foo" to gtv("FOO"), "bar" to gtv("<<<BAR>>>"))))
    }

    @Test
    fun toGtvEnum() {
        assertThat(GtvObjectMapper.toGtvDictionary(WithCustomEnum("FOO", CustomEnum.BAR))).isEqualTo(gtv(mapOf("foo" to gtv("FOO"), "bar" to gtv(1L))))
    }

    @Test
    fun toGtvInt() {
        assertThat(GtvObjectMapper.toGtvDictionary(IntField(17))).isEqualTo(gtv(mapOf("key" to gtv(17L))))
    }

    @ParameterizedTest(name = "Mapping from {1} to {2}")
    @MethodSource("acceptedTypes")
    fun mappingTest(input: Any, expected: GtvDictionary) {
        assertThat(GtvObjectMapper.toGtvDictionary(input)).isEqualTo(expected)
    }

    @ParameterizedTest(name = "Illegal type {1}")
    @MethodSource("illegalTypes")
    fun illegal(type: Any) {
        assertThrows<IllegalArgumentException> {
            GtvObjectMapper.toGtvDictionary(type)
        }
    }

    companion object {

        data class NullableType(@Nullable @Name("foo") val foo: Long?)
        data class NonAnnotatedConstructorParam(val foo: Long)

        @JvmStatic
        fun acceptedTypes() = listOf(
                arrayOf(Simple(1), gtv("key" to gtv(1))),
                arrayOf(BasicDict(Simple(2)), gtv("dict" to gtv("key" to gtv(2)))),
                arrayOf(NullableType(null), gtv("foo" to GtvNull)),
                arrayOf(DictWithList(listOf(Simple(1))), gtv("simples" to gtv(listOf(gtv("key" to gtv(1))))))
        )

        @JvmStatic
        fun illegalTypes() = listOf(
                arrayOf(listOf("foo")),
                arrayOf(setOf("foo")),
                arrayOf(NonAnnotatedConstructorParam(3)),
                arrayOf(UnsupportedConstructorParamType(3))
        )
    }
}
