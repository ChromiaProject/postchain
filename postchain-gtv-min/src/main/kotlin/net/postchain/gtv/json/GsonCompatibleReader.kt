package net.postchain.gtv.json

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvException
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString

/**
 * Reads exactly what `Gson.fromJson(String, Class)` reads, which is rather more than JSON.
 *
 * Transcribed from `com.google.gson.stream.JsonReader` in lenient mode, at the Gson version postchain-gtv
 * depends on. Every implementation of [GtvJsonCodec] routes [GtvJsonConfig.gsonCompatible] through this one
 * reader rather than through its own library's leniency: Jackson, for one, has no notion of an unquoted *value*
 * at all, and a compatibility mode that meant something different per implementation would be worse than
 * none.
 *
 * The whole document becomes a [Node] tree before any of it becomes a [Gtv], because that is the order the
 * original works in and the order is observable: a duplicate field name discards the earlier value while the
 * tree is being built, so `{"a": 1.5, "a": 1}` never asks what `1.5` means as a GTV integer.
 *
 * Faithful down to the accidents, each of them pinned by a vector recorded from Gson:
 *  - a number token is abandoned once it reaches [GSON_BUFFER] characters and re-read as a string, because
 *    that is the size of the buffer Gson scans it in;
 *  - `01` and `00` are strings, since a leading zero disqualifies a number;
 *  - `[1,]` is `[1, null]`, not `[1]`;
 *  - the keywords are matched per character against either case, so `TRUE` and `nUlL` are keywords;
 *  - a comment is fine ahead of the value and an error behind it.
 */
internal class GsonCompatibleReader(private val input: String) {

    private var pos = 0

    fun parse(): Gtv {
        consumeNonExecutePrefix()
        val gtv = readValue().toGtv()
        // Gson checks the document is fully consumed only once it has a value, and with strictness put back,
        // so nothing but whitespace may follow — not even a comment.
        while (pos < input.length && isWhitespace(input[pos])) pos++
        if (pos < input.length) fail("JSON document was not fully consumed")
        return gtv
    }

    private fun Node.toGtv(): Gtv = when (this) {
        is Node.Leaf -> gtv
        is Node.Number -> GtvJsonSupport.integerFromJsonNumber(text)
        is Node.Arr -> GtvArray(items.map { it.toGtv() }.toTypedArray())
        is Node.Obj -> GtvDictionary.build(entries.mapValues { it.value.toGtv() })
    }

    /** Gson skips the `)]}'` cross-site-scripting guard some servers prepend to JSON responses. */
    private fun consumeNonExecutePrefix() {
        skipToToken() ?: throw GtvException("Empty JSON input")
        pos--
        if (input.startsWith(")]}'\n", pos)) pos += 5
    }

    private fun readValue(): Node = when (require()) {
        '\'' -> Node.Leaf(GtvString(readQuoted('\'')))
        '"' -> Node.Leaf(GtvString(readQuoted('"')))
        '[' -> readArray()
        '{' -> readObject()
        // Only an array may hold the empty stretch between two separators; anywhere else it is an error.
        ']', ';', ',' -> fail("Unexpected value")
        else -> {
            pos--
            readLiteral()
        }
    }

    private fun readArray(): Node {
        val elements = mutableListOf<Node>()
        var first = true
        while (true) {
            if (!first) {
                when (require()) {
                    ']' -> return Node.Arr(elements)
                    ';', ',' -> Unit
                    else -> fail("Unterminated array")
                }
            }
            when (require()) {
                // A separator with nothing before it reads as null, so `[,]` holds two of them.
                ']' -> if (first) return Node.Arr(elements) else pushBackAndAddNull(elements)
                ';', ',' -> pushBackAndAddNull(elements)
                else -> {
                    pos--
                    elements.add(readValue())
                }
            }
            first = false
        }
    }

    private fun pushBackAndAddNull(elements: MutableList<Node>) {
        pos--
        elements.add(NULL)
    }

    private fun readObject(): Node {
        val entries = LinkedHashMap<String, Node>()
        var first = true
        while (true) {
            if (!first) {
                when (require()) {
                    '}' -> return Node.Obj(entries)
                    ';', ',' -> Unit
                    else -> fail("Unterminated object")
                }
            }
            val name = when (val c = require()) {
                '"' -> readQuoted('"')
                '\'' -> readQuoted('\'')
                // A trailing comma is fine in an array but not in an object.
                '}' -> if (first) return Node.Obj(entries) else fail("Expected name")
                else -> {
                    pos--
                    if (isLiteral(c)) readUnquoted() else fail("Expected name")
                }
            }
            when (require()) {
                ':' -> Unit
                '=' -> if (pos < input.length && input[pos] == '>') pos++
                else -> fail("Expected ':'")
            }
            entries[name] = readValue()
            first = false
        }
    }

    /** A bare token: a keyword, a number, or — failing both — a string. */
    private fun readLiteral(): Node {
        readKeyword()?.let { return it }
        readNumber()?.let { return it }
        if (!isLiteral(input[pos])) fail("Expected value")
        return Node.Leaf(GtvString(readUnquoted()))
    }

    private fun readKeyword(): Node? {
        val (word: String, value: Node) = when (input[pos]) {
            't', 'T' -> "true" to Node.Leaf(GtvInteger(1L))
            'f', 'F' -> "false" to Node.Leaf(GtvInteger(0L))
            'n', 'N' -> "null" to NULL
            else -> return null
        }
        if (pos + word.length > input.length) return null
        for (i in word.indices) {
            val c = input[pos + i]
            if (c != word[i] && c != word[i].uppercaseChar()) return null
        }
        // Don't match trues, falsey or nullsoft.
        if (pos + word.length < input.length && isLiteral(input[pos + word.length])) return null
        pos += word.length
        return value
    }

    /**
     * Returns null when what follows is not a number, leaving [pos] alone so it can be re-read as a string.
     *
     * The `value` accumulator exists only to reproduce Gson's leading-zero rule, which asks whether the digits
     * so far come to zero rather than whether the first one was a `0`.
     */
    private fun readNumber(): Node? {
        var i = 0
        var last = NumberChar.NONE
        var value = 0L
        while (true) {
            if (i == GSON_BUFFER) return null
            if (pos + i == input.length) break
            val c = input[pos + i]
            when {
                c == '-' -> when (last) {
                    NumberChar.NONE -> last = NumberChar.SIGN
                    NumberChar.EXP_E -> last = NumberChar.EXP_SIGN
                    else -> return null
                }

                c == '+' -> if (last == NumberChar.EXP_E) last = NumberChar.EXP_SIGN else return null

                c == 'e' || c == 'E' ->
                    if (last == NumberChar.DIGIT || last == NumberChar.FRACTION_DIGIT) {
                        last = NumberChar.EXP_E
                    } else {
                        return null
                    }

                c == '.' -> if (last == NumberChar.DIGIT) last = NumberChar.DECIMAL else return null

                c in '0'..'9' -> when (last) {
                    NumberChar.NONE, NumberChar.SIGN -> {
                        value = -(c - '0').toLong()
                        last = NumberChar.DIGIT
                    }

                    NumberChar.DIGIT -> {
                        if (value == 0L) return null
                        value = value * 10 - (c - '0')
                    }

                    NumberChar.DECIMAL -> last = NumberChar.FRACTION_DIGIT
                    NumberChar.EXP_E, NumberChar.EXP_SIGN -> last = NumberChar.EXP_DIGIT
                    else -> Unit
                }

                !isLiteral(c) -> break
                else -> return null
            }
            i++
        }
        if (last != NumberChar.DIGIT && last != NumberChar.FRACTION_DIGIT && last != NumberChar.EXP_DIGIT) {
            return null
        }
        val text = input.substring(pos, pos + i)
        pos += i
        return Node.Number(text)
    }

    private fun readUnquoted(): String {
        val start = pos
        while (pos < input.length && isLiteral(input[pos])) pos++
        return input.substring(start, pos)
    }

    /** The opening [quote] has been consumed; consumes the closing one too. */
    private fun readQuoted(quote: Char): String {
        var builder: StringBuilder? = null
        var start = pos
        while (pos < input.length) {
            when (input[pos++]) {
                quote -> {
                    val end = pos - 1
                    return builder?.append(input, start, end)?.toString() ?: input.substring(start, end)
                }

                '\\' -> {
                    val target = builder ?: StringBuilder().also { builder = it }
                    target.append(input, start, pos - 1)
                    target.append(readEscape())
                    start = pos
                }
            }
        }
        fail("Unterminated string")
    }

    private fun readEscape(): Char {
        if (pos == input.length) fail("Unterminated escape sequence")
        return when (val escaped = input[pos++]) {
            'u' -> readUnicodeEscape()
            't' -> '\t'
            'b' -> '\u0008'
            'n' -> '\n'
            'r' -> '\r'
            'f' -> '\u000C'
            // A backslash before a literal newline, and `\'`, are both errors in JSON and both fine here.
            '\n', '\'', '"', '\\', '/' -> escaped
            else -> fail("Invalid escape sequence")
        }
    }

    private fun readUnicodeEscape(): Char {
        if (pos + 4 > input.length) fail("Unterminated escape sequence")
        var result = 0
        for (i in pos until pos + 4) {
            val digit = when (val c = input[i]) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> fail("Malformed Unicode escape")
            }
            result = (result shl 4) + digit
        }
        pos += 4
        return result.toChar()
    }

    private fun require(): Char = skipToToken() ?: fail("End of input")

    /** Consumes and returns the next character that is neither whitespace nor part of a comment. */
    private fun skipToToken(): Char? {
        while (pos < input.length) {
            when (val c = input[pos++]) {
                ' ', '\t', '\r', '\n' -> Unit

                '/' -> {
                    if (pos == input.length) return c
                    when (input[pos]) {
                        '*' -> {
                            pos++
                            val end = input.indexOf("*/", pos)
                            if (end < 0) fail("Unterminated comment")
                            pos = end + 2
                        }

                        '/' -> {
                            pos++
                            skipToEndOfLine()
                        }

                        else -> return c
                    }
                }

                '#' -> skipToEndOfLine()

                else -> return c
            }
        }
        return null
    }

    private fun isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r' || c == '\n'

    private fun skipToEndOfLine() {
        while (pos < input.length) {
            val c = input[pos++]
            if (c == '\n' || c == '\r') break
        }
    }

    /** Whether [c] can be part of a bare token. Everything Gson treats as punctuation is excluded. */
    private fun isLiteral(c: Char): Boolean = when (c) {
        '/', '\\', ';', '#', '=', '{', '}', '[', ']', ':', ',', ' ', '\t', '\u000C', '\r', '\n' -> false
        else -> true
    }

    private fun fail(message: String): Nothing = throw GtvException("$message at offset $pos")

    private enum class NumberChar { NONE, SIGN, DIGIT, DECIMAL, FRACTION_DIGIT, EXP_E, EXP_SIGN, EXP_DIGIT }

    /** The document as parsed, before anything has been asked to be a [Gtv]. Mirrors Gson's `JsonElement`. */
    private sealed interface Node {
        /** Anything whose GTV form is already settled: null, the two booleans, and every string. */
        class Leaf(val gtv: Gtv) : Node

        /** Kept as text, because whether it is a GTV integer is not decided until the tree is complete. */
        class Number(val text: String) : Node

        class Arr(val items: List<Node>) : Node
        class Obj(val entries: Map<String, Node>) : Node
    }

    private companion object {
        val NULL = Node.Leaf(GtvNull)

        /**
         * Gson scans a candidate number inside a buffer this size and gives up when the token fills it, at
         * which point the token comes back as a string instead. 1024 digits is well past anything a `long`
         * could hold, so this only ever changes an error into a `GtvString`.
         */
        const val GSON_BUFFER = 1024
    }
}
