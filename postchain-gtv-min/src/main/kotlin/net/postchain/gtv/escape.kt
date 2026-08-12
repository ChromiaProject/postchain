package net.postchain.gtv

/**
 * Escapes a string the way GTV's textual notation expects: the usual backslash escapes, plus `\uXXXX` for any
 * code point outside `[0x20, 0xFFEF]`. Supplementary code points are escaped as their two surrogates, which is
 * what postchain-gtv's `ESCAPE_GTV` translator chain produces.
 */
internal fun escapeGtv(s: String): String = buildString(s.length) {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        val handled = when (c) {
            '\'' -> "\\'"
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\b' -> "\\b"
            '\n' -> "\\n"
            '\t' -> "\\t"
            '\r' -> "\\r"
            else -> null
        }
        if (handled != null) {
            append(handled)
            i++
            continue
        }

        val codePoint = codePointAt(s, i)
        if (codePoint < 0x0020 || codePoint > 0xFFEF) {
            // Both halves of a surrogate pair get their own escape.
            val units = if (codePoint > 0xFFFF) 2 else 1
            repeat(units) {
                append("\\u")
                append(hex4(s[i + it]))
            }
            i += units
        } else {
            append(c)
            i++
        }
    }
}

private fun codePointAt(s: String, index: Int): Int {
    val high = s[index]
    if (high.isHighSurrogate() && index + 1 < s.length && s[index + 1].isLowSurrogate()) {
        return 0x10000 + ((high.code - 0xD800) shl 10) + (s[index + 1].code - 0xDC00)
    }
    return high.code
}

private fun hex4(c: Char): String = c.code.toString(16).uppercase().padStart(4, '0')
