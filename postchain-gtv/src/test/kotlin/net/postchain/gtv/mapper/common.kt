package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory

data class Simple(@param:Name("key") val value: Long)

data class IntField(@param:Name("key") val value: Int)

data class BasicDict(@param:Name("dict") val simple: Simple)

@Suppress("UNUSED")
enum class SimpleEnum {
    A, B
}

data class DictWithList(
        @param:Name("simples") val simples: List<Simple>
)

data class Custom(private val v: String) : ToGtv {
    override fun toGtv(): Gtv = GtvFactory.gtv("<<<$v>>>")

    companion object : FromGtv<Custom> {
        override fun fromGtv(gtv: Gtv) = Custom(gtv.asString().drop(3).dropLast(3))
    }
}

data class WithCustom(@param:Name("foo") val foo: String, @param:Name("bar") val bar: Custom)

enum class CustomEnum: ToGtv {
    FOO, BAR;
    override fun toGtv(): Gtv = GtvFactory.gtv(ordinal.toLong())
}

data class WithCustomEnum(@param:Name("foo") val foo: String, @param:Name("bar") val bar: CustomEnum)

data class UnsupportedConstructorParamType(@param:Name("foo") val foo: Short)
