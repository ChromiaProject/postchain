package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString

object GtvParser {
    fun parse(s: String): Gtv {
        return when {
            s == "null" -> GtvNull
            s.startsWith("x\"") && s.endsWith(Typography.quote) -> encodeByteArray(s.substring(1))
            s.startsWith("[") && s.endsWith("]") -> encodeArray(s.trim('[', ']'))
            s.startsWith("{") && s.endsWith("}") -> encodeDict(s.trim('{', '}'))
            else -> s.toLongOrNull()?.let(::GtvInteger) ?: GtvString(s.trim(Typography.quote))
        }
    }

    private fun encodeByteArray(arg: String): Gtv {
        val bytearray = arg.trim(Typography.quote)
        return try {
            GtvFactory.gtv(bytearray.hexStringToByteArray())
        } catch (e: IllegalArgumentException) {
            GtvFactory.gtv(bytearray.toByteArray())
        }
    }

    private fun encodeArray(arg: String) = GtvFactory.gtv(arg.split(",").map { parse(it) })

    private fun encodeDict(arg: String): Gtv {
        val pairs = arg.split(",").map { it.split("=", limit = 2) } // TODO: splitting on , makes it impossible to use this delimiter in string value.
        if (pairs.any { it.size < 2 }) throw IllegalArgumentException("Wrong format. Expected dict $arg to contain key=value pairs")
        return GtvFactory.gtv(pairs.associateBy({ it[0].trim() }, { parse(it[1].trim()) }))
    }
}
