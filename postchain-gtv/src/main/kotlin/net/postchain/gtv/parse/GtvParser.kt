package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString

object GtvParser {
    fun parse(str: String): Gtv {
        val s = str.trim()
        return when {
            s == "null" -> GtvNull
            s.startsWith("x\"") && s.endsWith(Typography.quote) -> encodeByteArray(s.substring(1))
            s.startsWith("[") && s.endsWith("]") -> encodeArray(s.removeSurrounding("[", "]"))
            s.startsWith("{") && s.endsWith("}") -> encodeDict(s.removeSurrounding("{", "}"))
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

    private fun encodeDict(arg: String): Gtv {
        val pairs = arg.split(",").map { it.split("=", limit = 2) } // TODO: splitting on , makes it impossible to use this delimiter in string value.
        if (pairs.any { it.size < 2 }) throw IllegalArgumentException("Wrong format. Expected dict $arg to contain key=value pairs")
        return GtvFactory.gtv(pairs.associateBy({ it[0].trim() }, { parse(it[1].trim()) }))
    }

    private fun encodeArray(arg: String): Gtv {
        val elements = mutableListOf<Gtv>()
        var startIndex = 0
        var bracketCount = 0

        for (i in arg.indices) {
            when (arg[i]) {
                '[' -> bracketCount++
                ']' -> bracketCount--
                ',' -> {
                    if (bracketCount == 0) {
                        elements.add(parse(arg.substring(startIndex, i)))
                        startIndex = i + 1
                    }
                }
            }
        }

        elements.add(parse(arg.substring(startIndex)))
        return GtvFactory.gtv(elements)
    }

}
