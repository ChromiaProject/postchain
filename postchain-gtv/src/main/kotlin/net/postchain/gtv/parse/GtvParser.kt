package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString

object GtvParser {

    private val UNESCAPED_EQUAL_SIGN_PATTERN = "(?<!\\\\)=".toRegex()

    fun parse(str: String, isDictValue: Boolean = false): Gtv {
        val s = str.trim()
        return when {
            s == "null" -> GtvNull
            s.startsWith("x\"") && s.endsWith(Typography.quote) -> parseByteArray(s.substring(1))
            s.startsWith("[") && s.endsWith("]") -> parseArray(s.removeSurrounding("[", "]"))
            s.startsWith("{") && s.endsWith("}") -> parseDict(s.removeSurrounding("{", "}"))
            s.endsWith("L") -> {
                s.substring(0, s.length - 1).toBigIntegerOrNull()?.let { GtvBigInteger(it) }
                        ?: if (isDictValue) parseDictValueString(s) else GtvString(s)
            }

            else -> s.toLongOrNull()?.let(::GtvInteger)
                    ?: if (isDictValue) parseDictValueString(s) else GtvString(s.trim(Typography.quote))
        }
    }

    private fun parseByteArray(arg: String): Gtv {
        val bytearray = arg.trim(Typography.quote)
        return gtv(bytearray.hexStringToByteArray())
    }

    private fun parseDict(str: String) = gtv(splitArray(str).associate {
        if (!it.contains("=")) throw IllegalArgumentException("$it must be a key-value pair separated by \"=\"")
        val (key, value) = it.split("=", limit = 2)
        key.trim() to parse(value, true)
    })

    private fun parseDictValueString(arg: String): Gtv {
        val dictValueString = arg.trim(Typography.quote)
        if (dictValueString.contains(UNESCAPED_EQUAL_SIGN_PATTERN)) {
            throw IllegalArgumentException("\"=\" characters inside dict string values must be escaped by a preceding" +
                    " \"\\\" character. Ensure that you are using the correct separator \",\" for dict key-value pairs.")
        }
        return gtv(dictValueString.replace("\\=", "="))
    }

    private fun parseArray(str: String) = gtv(splitArray(str).map { parse(it) })

    private fun splitArray(str: String) = buildList {
        var startIndex = 0
        var bracketCount = 0
        var isQuote = false

        for (i in str.indices) {
            when (str[i]) {
                Typography.quote -> isQuote = !isQuote
                '[', '{' -> bracketCount++
                ']', '}' -> bracketCount--
                ',' -> {
                    if (bracketCount == 0 && !isQuote) {
                        add(str.substring(startIndex, i))
                        startIndex = i + 1
                    }
                }
            }
        }
        if (startIndex < str.length) add(str.substring(startIndex))
    }
}
