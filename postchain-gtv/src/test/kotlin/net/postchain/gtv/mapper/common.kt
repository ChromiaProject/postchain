package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory

data class Simple(@Name("key") val value: Long)

data class BasicDict(@Name("dict") val simple: Simple)

@Suppress("UNUSED")
enum class SimpleEnum {
    A, B
}

data class DictWithList(
        @Name("simples") val simples: List<Simple>
)

data class Custom(private val v: String) : ToGtv {
    override fun toGtv(): Gtv = GtvFactory.gtv("<<<$v>>>")

    companion object : FromGtv<Custom> {
        override fun fromGtv(gtv: Gtv) = Custom(gtv.asString().drop(3).dropLast(3))
    }
}

data class WithCustom(@Name("foo") val foo: String, @Name("bar") val bar: Custom)

enum class CustomEnum: ToGtv {
    FOO, BAR;
    override fun toGtv(): Gtv = GtvFactory.gtv(ordinal.toLong())
}

data class WithCustomEnum(@Name("foo") val foo: String, @Name("bar") val bar: CustomEnum)

data class UnsupportedConstructorParamType(@Name("foo") val foo: Int)
