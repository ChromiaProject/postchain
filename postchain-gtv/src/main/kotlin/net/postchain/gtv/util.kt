package net.postchain.gtv

import org.apache.commons.text.translate.AggregateTranslator
import org.apache.commons.text.translate.JavaUnicodeEscaper
import org.apache.commons.text.translate.LookupTranslator

val ESCAPE_GTV = AggregateTranslator(LookupTranslator(mapOf<CharSequence, CharSequence>(
        "'" to "\\'",
        "\"" to "\\\"",
        "\\" to "\\\\",
        "\b" to "\\b",
        "\n" to "\\n",
        "\t" to "\\t",
        "\r" to "\\r"
)), JavaUnicodeEscaper.outsideOf(32, 0x7f))
