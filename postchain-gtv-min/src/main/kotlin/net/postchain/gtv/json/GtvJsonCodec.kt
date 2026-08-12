package net.postchain.gtv.json

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvException
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.json.GtvJsonSupport.GSON_NUMBER_LIMIT
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.math.BigDecimal
import kotlin.math.abs

/**
 * How a [Gtv] maps onto JSON.
 *
 * The mapping is lossy in the same places postchain-gtv's Gson bindings are: byte arrays become hex strings and
 * integers become JSON numbers, so nothing distinguishes them from strings and `int`s when reading back.
 *
 * @property bigIntegerAsString serialize `big_integer` as a JSON string rather than a bare number. A bare number
 *   is out of range for most JSON readers, so this is the safe choice.
 * @property supportBigInteger allow `big_integer` at all. The default rejects it, matching the Gson bindings,
 *   because a value that cannot survive a round trip is usually a bug at the call site.
 * @property htmlSafe escape `<`, `>`, `&`, `=` and `'` as `\uXXXX`. On by default because Gson does it by
 *   default, and matching the Gson bindings' bytes matters more here than terser output. The JSON is equivalent
 *   either way, so turn it off if you prefer the characters unescaped.
 * @property gsonCompatible accept the malformed JSON that Gson accepts. `Gson.fromJson(String, Class)`
 *   parses leniently, so documents written for it are not always JSON, and refusing them is the one thing that
 *   stops this codec being a drop-in replacement for them. Off by default, and it changes reading only — encoding is
 *   byte-identical either way. Turning it on additionally accepts: single-quoted strings and field names;
 *   unquoted field names; unquoted string values, including ones that only look like numbers, so `bye` and
 *   `12EF` read as the strings `"bye"` and `"12EF"`; `//`, `/* */` and `#` comments anywhere except after the
 *   document's last value; `;` where a `,` belongs and `=` or `=>` where a `:` belongs; any capitalisation of
 *   `true`, `false` and `null`; a missing array element as null, so `[1,]` reads as `[1, null]`; and a leading
 *   `)]}'` prefix. Every implementation shares one reader for this, so they accept exactly the same text.
 */
public class GtvJsonConfig(
    public val bigIntegerAsString: Boolean = true,
    public val supportBigInteger: Boolean = false,
    public val htmlSafe: Boolean = true,
    public val gsonCompatible: Boolean = false,
) {
    public companion object {
        /** Rejects `big_integer`. Equivalent to `make_gtv_gson()`. */
        public val Default: GtvJsonConfig = GtvJsonConfig()

        /** Serializes `big_integer` as a JSON string. Equivalent to `makeStrictGtvGson()`. */
        public val Strict: GtvJsonConfig = GtvJsonConfig(bigIntegerAsString = true, supportBigInteger = true)

        /** Serializes `big_integer` as a bare JSON number. Equivalent to `makeLenientGtvGson()`. */
        public val Lenient: GtvJsonConfig = GtvJsonConfig(bigIntegerAsString = false, supportBigInteger = true)
    }
}

/**
 * Converts between [Gtv] and JSON.
 *
 * Implemented once per JSON library. Both implementations are held to the same contract, so which one you depend
 * on is purely a question of which library you already have on the classpath.
 *
 * JSON is defined as UTF-8, and an implementation can read and write those bytes without ever materialising a
 * `String`. Prefer the byte and stream overloads whenever the JSON is headed for a socket, a file or a database
 * column — the `String` ones cost an extra UTF-16 copy in each direction.
 */
public interface GtvJsonCodec {

    public fun encodeToString(gtv: Gtv): String

    /** UTF-8 bytes. */
    public fun encodeToByteArray(gtv: Gtv): ByteArray {
        val buffer = ByteArrayOutputStream(DEFAULT_BUFFER)
        encodeTo(gtv, buffer)
        return buffer.toByteArray()
    }

    /** Writes UTF-8 bytes to [output]. Does not close it. */
    public fun encodeTo(gtv: Gtv, output: OutputStream)

    public fun decodeFromString(text: String): Gtv

    /** Reads UTF-8 bytes. */
    public fun decodeFromByteArray(bytes: ByteArray): Gtv = decodeFrom(ByteArrayInputStream(bytes))

    /** Reads UTF-8 bytes from [input]. Does not close it. */
    public fun decodeFrom(input: InputStream): Gtv

    private companion object {
        const val DEFAULT_BUFFER = 1024
    }
}

/**
 * The pieces of the mapping that do not depend on a JSON library, kept here so both codecs agree on them exactly.
 */
public object GtvJsonSupport {

    /**
     * Applies the JSON-number rule: GTV has no floating point, and its integers are 64-bit.
     *
     * "Integer" means integer-*valued*, not integer-spelled, which is what the Gson bindings'
     * `BigDecimal.longValueExact()` means: `123.0` and `1e3` are accepted, `123.456` is not.
     *
     * @param content the number exactly as it appeared in the document
     */
    public fun integerFromJsonNumber(content: String): GtvInteger {
        // The spelling that needs no BigDecimal, which is very nearly all of them. Every string this accepts,
        // BigDecimal would accept with the same value.
        content.toLongOrNull()?.let { return GtvInteger(it) }
        return GtvInteger(
            try {
                gsonBigDecimal(content).longValueExact()
            } catch (_: ArithmeticException) {
                throw GtvException(numberErrorMessage(content))
            } catch (_: NumberFormatException) {
                throw GtvException(numberErrorMessage(content))
            }
        )
    }

    private fun numberErrorMessage(content: String) =
        "Could not deserialize number '$content' to GtvInteger, valid numbers must be integers " +
            "and be in range: [-2^63, (2^63)-1]"

    /**
     * Gson refuses a number whose text or scale runs past [GSON_NUMBER_LIMIT] before [BigDecimal] ever sees it.
     * Every such number is out of `long` range anyway, bar one family: `0e-10000` is zero, and Gson
     * still rejects it.
     */
    private fun gsonBigDecimal(content: String): BigDecimal {
        if (content.length > GSON_NUMBER_LIMIT) throw NumberFormatException("Number string too large")
        val decimal = BigDecimal(content)
        if (abs(decimal.scale().toLong()) >= GSON_NUMBER_LIMIT) {
            throw NumberFormatException("Number has unsupported scale: $content")
        }
        return decimal
    }

    private const val GSON_NUMBER_LIMIT = 10_000

    /**
     * Reads JSON the way `Gson.fromJson(String, Class)` reads it — see [GtvJsonConfig.gsonCompatible] for what
     * that lets through. Lives here rather than in a backend so that every backend accepts the same text: no
     * JSON library's own lenient mode has Gson's rules.
     */
    public fun decodeGsonCompatible(text: String): Gtv = GsonCompatibleReader(text).parse()

    public fun bigIntegerUnsupported(): Nothing = throw GtvException("big_integer cannot be serialized as JSON")

    /**
     * Escapes the characters Gson escapes beyond what JSON demands: U+2028 and U+2029 always, and the five
     * HTML-significant characters when [htmlSafe].
     *
     * U+2028 and U+2029 are legal in a JSON string but terminate a line in JavaScript, so Gson escapes them
     * whatever else it is doing, and a codec that leaves them alone writes different bytes for the same value.
     *
     * Safe to apply to finished JSON: none of these characters can appear outside a string literal.
     */
    public fun escapeGsonSpecials(json: String, htmlSafe: Boolean): String {
        val sb = StringBuilder(json.length)
        for (c in json) {
            val escape = escapeOf(c.code, htmlSafe)
            if (escape == null) sb.append(c) else sb.append(escape)
        }
        return sb.toString()
    }

    /** The streaming counterpart of [escapeGsonSpecials]. Does not close [target]. */
    public fun escapingWriter(target: Writer, htmlSafe: Boolean): Writer = EscapingWriter(target, htmlSafe)

    internal fun escapeOf(code: Int, htmlSafe: Boolean): String? = when (code) {
        0x2028 -> "\\u2028"
        0x2029 -> "\\u2029"
        '<'.code -> if (htmlSafe) "\\u003c" else null
        '>'.code -> if (htmlSafe) "\\u003e" else null
        '&'.code -> if (htmlSafe) "\\u0026" else null
        '='.code -> if (htmlSafe) "\\u003d" else null
        '\''.code -> if (htmlSafe) "\\u0027" else null
        else -> null
    }
}

private class EscapingWriter(private val out: Writer, private val htmlSafe: Boolean) : Writer() {
    override fun write(c: Int) {
        val escape = GtvJsonSupport.escapeOf(c, htmlSafe)
        if (escape == null) out.write(c) else out.write(escape)
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        // Pass through the longest runs possible, so the common case stays a bulk copy.
        var runStart = off
        for (i in off until off + len) {
            val escape = GtvJsonSupport.escapeOf(cbuf[i].code, htmlSafe) ?: continue
            if (i > runStart) out.write(cbuf, runStart, i - runStart)
            out.write(escape)
            runStart = i + 1
        }
        if (runStart < off + len) out.write(cbuf, runStart, off + len - runStart)
    }

    override fun write(str: String, off: Int, len: Int) = write(str.toCharArray(), off, len)

    override fun flush(): Unit = out.flush()

    // Deliberately does not close the delegate: the codec does not own the caller's stream.
    override fun close(): Unit = out.flush()
}
