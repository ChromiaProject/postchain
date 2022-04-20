package net.postchain.client.cli

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import kotlin.text.Typography.quote

fun encodeArg(arg: String): Gtv {
    return when {
        arg.startsWith("x\"") && arg.endsWith(quote) -> encodeByteArray(arg.substring(1))
        arg.startsWith("[") && arg.endsWith("]") -> encodeArray(arg.trim('[', ']'))
        arg.startsWith("{") && arg.endsWith("}") -> encodeDict(arg.trim('{', '}'))
        else -> arg.toLongOrNull()?.let(::GtvInteger) ?: GtvString(arg.trim(quote))
    }
}

private fun encodeByteArray(arg: String): Gtv {
    val bytearray = arg.trim(quote)
    return try {
        GtvFactory.gtv(bytearray.hexStringToByteArray())
    } catch (e: IllegalArgumentException) {
        GtvFactory.gtv(bytearray.toByteArray())
    }
}

private fun encodeArray(arg: String) = GtvFactory.gtv(arg.split(",").map { encodeArg(it) })

private fun encodeDict(arg: String): Gtv {
    val pairs = arg.split(",").map { it.split("=", limit = 2) }
    if (pairs.any { it.size < 2 }) throw IllegalArgumentException("Wrong format. Expected dict $arg to contain key=value pairs")
    return GtvFactory.gtv(pairs.associateBy({ it[0] }, { encodeArg(it[1]) }))
}
