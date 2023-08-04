package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import java.math.BigInteger

data class Simple(@Name("key") val value: Long)

data class BasicDict(@Name("dict") val simple: Simple)

@Suppress("UNUSED")
enum class SimpleEnum {
    A, B
}

enum class RellEnum {
    A, B;
    fun getValueForRell() = this.ordinal.toLong()
}

enum class RellEnumWithCustomValue(val customValue: Long) {
    A(1), B(2);
    fun getValueForRell() = BigInteger.valueOf(this.customValue)
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

data class UnsupportedConstructorParamType(@Name("foo") val foo: Int)
