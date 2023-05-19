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
        val pairs = mutableListOf<Pair<String, Gtv>>()

        var currentIndex = 0
        var bracketCount = 0
        for (i in arg.indices) {
            when (arg[i]) {
                '{' -> bracketCount++
                '}' -> bracketCount--
                '=' -> {
                    if (bracketCount == 0) {
                        val key = arg.substring(currentIndex, i).trim()
                        val valueStrRest = arg.substring(i + 1)
                        val valueEnd = findValueEnd(valueStrRest)
                        val valueStr = valueStrRest.substring(0, valueEnd)
                        val value = parse(valueStr.trim())
                        pairs.add(key to value)
                        currentIndex = i + 2 + valueStr.length
                    }
                }
            }
        }
        return GtvFactory.gtv(pairs.toMap())
    }

    private fun findValueEnd(str: String): Int {
        var bracketCount = 0
        for (i in str.indices) {
            when (str[i]) {
                '[', '{' -> bracketCount++
                ']', '}' -> bracketCount--
                ',' -> {
                    if (bracketCount == 0) return i
                }
            }
        }
        return str.length
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
