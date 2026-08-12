package net.postchain.gtv.json.jackson

import com.fasterxml.jackson.core.SerializableString
import com.fasterxml.jackson.core.io.CharacterEscapes
import com.fasterxml.jackson.core.io.SerializedString

/**
 * Makes Jackson write `\u001d` rather than `\u001D`.
 *
 * Jackson uppercases the hex digits of a `\uXXXX` escape; Gson lowercases them. The JSON is equivalent, but GTV
 * is consensus-critical and this codec promises output byte-identical to the Gson bindings in postchain-gtv, so
 * Jackson is the one that has to move.
 *
 * Only the escapes Jackson would render as `\uXXXX` are overridden. The short forms — `\n`, `\t`, `\r`, `\b`,
 * `\f`, `\"`, `\\` — are left to the standard table, which already agrees.
 */
internal object LowercaseUnicodeEscapes : CharacterEscapes() {

    private val escapeCodes: IntArray = standardAsciiEscapesForJSON().also { codes ->
        for (i in codes.indices) {
            if (codes[i] == ESCAPE_STANDARD) codes[i] = ESCAPE_CUSTOM
        }
    }

    private val sequences: Array<SerializableString?> = arrayOfNulls<SerializableString>(0x20).also { table ->
        for (i in 0 until 0x20) {
            if (escapeCodes[i] == ESCAPE_CUSTOM) {
                table[i] = SerializedString("\\u%04x".format(i))
            }
        }
    }

    // Jackson may mutate the array it is given, so hand out a copy.
    override fun getEscapeCodesForAscii(): IntArray = escapeCodes.copyOf()

    // null means "no custom escape": anything outside the control range is written as itself, which is what
    // Gson does with non-ASCII too.
    override fun getEscapeSequence(ch: Int): SerializableString? = sequences.getOrNull(ch)
}
