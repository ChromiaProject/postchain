package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString

object GtvParser {
    fun parse(str: String): Gtv {
        val s = str.trim()
        return when {
            s == "null" -> GtvNull
            s.startsWith("x\"") && s.endsWith(Typography.quote) -> parseByteArray(s.substring(1))
            s.startsWith("[") && s.endsWith("]") -> parseArray(s.removeSurrounding("[", "]"))
            s.startsWith("{") && s.endsWith("}") -> parseDict(s.removeSurrounding("{", "}"))
            else -> s.toLongOrNull()?.let(::GtvInteger) ?: GtvString(s.trim(Typography.quote))
        }
    }

    private fun parseByteArray(arg: String): Gtv {
        val bytearray = arg.trim(Typography.quote)
        return try {
            gtv(bytearray.hexStringToByteArray())
        } catch (e: IllegalArgumentException) {
            gtv(bytearray.toByteArray())
        }
    }


    private fun parseDict(str: String): Gtv {
        return gtv(buildMap {
            splitArray(str).forEach {
                if (!it.contains("=")) throw IllegalArgumentException("$it must be encoded as a key-value pair")
                val (key, value) = it.split("=", limit = 2)
                put(key.trim(), parse(value))
            }
        })
    }

    private fun parseArray(str: String) = gtv(splitArray(str).map { parse(it) })

    private fun splitArray(str: String): List<String> {
        return buildList {
            var startIndex = 0
            var bracketCount = 0

            for (i in str.indices) {
                when (str[i]) {
                    '[', '{' -> bracketCount++
                    ']', '}' -> bracketCount--
                    ',' -> {
                        if (bracketCount == 0) {
                            add(str.substring(startIndex, i))
                            startIndex = i + 1
                        }
                    }
                }
            }
            if (startIndex < str.length) add(str.substring(startIndex))
        }
    }

}
