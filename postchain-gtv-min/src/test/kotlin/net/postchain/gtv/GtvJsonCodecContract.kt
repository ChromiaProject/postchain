package net.postchain.gtv

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.json.GtvJsonCodec
import net.postchain.gtv.json.GtvJsonConfig
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The behaviour every [GtvJsonCodec] must have, run once per implementation.
 *
 * The expectations come from [VECTORS], which were recorded from the Gson bindings in postchain-gtv, so
 * passing this means a codec is byte-compatible with them — and, transitively, that any two codecs that pass
 * are interchangeable.
 */
abstract class GtvJsonCodecContract {

    /** @param prettyPrint two-space indentation, matching Gson's `setPrettyPrinting()` */
    protected abstract fun codec(config: GtvJsonConfig, prettyPrint: Boolean = false): GtvJsonCodec

    private val compact get() = codec(GtvJsonConfig.Default)

    private val lenient get() = codec(GtvJsonConfig.Lenient)

    private val pretty get() = codec(GtvJsonConfig.Lenient, prettyPrint = true)

    private val gsonCompatible get() = codec(GtvJsonConfig(gsonCompatible = true))

    /** Vectors whose reference run threw are recorded as `!ExceptionName`. */
    private fun threw(value: String) = value.startsWith("!")

    @Test
    fun matchesReferenceJson() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())

            if (threw(vector.json)) {
                assertFailsWith<GtvException>("json ${vector.name}") { compact.encodeToString(gtv) }
            } else {
                assertEquals(vector.json, compact.encodeToString(gtv), "json ${vector.name}")
            }

            if (!threw(vector.jsonLenient)) {
                assertEquals(vector.jsonLenient, lenient.encodeToString(gtv), "jsonLenient ${vector.name}")
                assertEquals(vector.jsonPretty, pretty.encodeToString(gtv), "jsonPretty ${vector.name}")
            }
        }
    }

    @Test
    fun readsBackEveryJsonRepresentableVector() {
        for (vector in VECTORS) {
            if (threw(vector.json)) continue
            // Byte arrays become hex strings, so reading back does not always give the original value. What the
            // mapping does promise is that re-encoding what was read produces the same text.
            val decoded = compact.decodeFromString(vector.json)
            assertEquals(vector.json, compact.encodeToString(decoded), "json round trip ${vector.name}")
        }
    }

    @Test
    fun everyIoFormAgreesWithTheStringForm() {
        for (vector in VECTORS) {
            if (threw(vector.json)) continue
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            val expected = vector.json

            assertEquals(expected, compact.encodeToByteArray(gtv).decodeToString(), "bytes ${vector.name}")

            val stream = ByteArrayOutputStream()
            compact.encodeTo(gtv, stream)
            assertEquals(expected, stream.toByteArray().decodeToString(), "stream ${vector.name}")

            val fromBytes = compact.decodeFromByteArray(expected.encodeToByteArray())
            assertEquals(expected, compact.encodeToString(fromBytes), "decode bytes ${vector.name}")

            val fromStream = compact.decodeFrom(ByteArrayInputStream(expected.encodeToByteArray()))
            assertEquals(expected, compact.encodeToString(fromStream), "decode stream ${vector.name}")
        }
    }

    @Test
    fun writesUtf8Bytes() {
        // Non-ASCII must come out as UTF-8, not as escapes and not as the platform encoding.
        val gtv = GtvString("R\u00e4ksm\u00f6rg\u00e5s \ud83d\ude00")
        val codec = codec(GtvJsonConfig.Default)
        val bytes = codec.encodeToByteArray(gtv)
        assertContentEquals(codec.encodeToString(gtv).encodeToByteArray(), bytes, "utf-8 bytes")
        assertEquals(gtv, codec.decodeFromByteArray(bytes))
    }

    @Test
    fun doesNotCloseTheCallersStream() {
        val output = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }
        }
        codec(GtvJsonConfig.Default).encodeTo(GtvInteger(1), output)
        assertTrue(!output.closed, "codec must not close a stream it does not own")
        assertEquals("1", output.toByteArray().decodeToString())
    }

    @Test
    fun readsBackWhatItWrites() {
        val gtv = gtv(
            "s" to GtvString("hello"),
            "i" to GtvInteger(-7),
            "n" to GtvNull,
            "a" to gtv(GtvInteger(1), GtvString("2")),
            "nested" to gtv("deep" to gtv(GtvInteger(0))),
        )
        val codec = codec(GtvJsonConfig.Default)
        assertEquals(gtv, codec.decodeFromString(codec.encodeToString(gtv)))
    }

    @Test
    fun booleansBecomeIntegers() {
        val codec = codec(GtvJsonConfig.Default)
        assertEquals(GtvInteger(1), codec.decodeFromString("true"))
        assertEquals(GtvInteger(0), codec.decodeFromString("false"))
    }

    @Test
    fun rejectsNonIntegralNumbers() {
        val codec = codec(GtvJsonConfig.Default)
        assertFailsWith<GtvException> { codec.decodeFromString("1.5") }
        assertFailsWith<GtvException> { codec.decodeFromString("99999999999999999999") }
    }

    @Test
    fun acceptsWholeValuedDecimals() {
        // "Integer" means integer-valued, not integer-spelled: Gson reads the number as a BigDecimal
        // and calls longValueExact(), which is happy with a zero fractional part or an exponent.
        val codec = codec(GtvJsonConfig.Default)
        assertEquals(GtvInteger(123), codec.decodeFromString("123.0"))
        assertEquals(GtvInteger(1000), codec.decodeFromString("1e3"))
        assertEquals(GtvInteger(-100000), codec.decodeFromString("-1E5"))
        assertFailsWith<GtvException> { codec.decodeFromString("123.0000000001") }
    }

    @Test
    fun matchesReferenceJsonDecoding() {
        // The reference is Gson, which always parses leniently, so these run against compatibility mode.
        for (vector in JSON_DECODE_VECTORS) {
            val what = "decode <<${vector.input}>>"
            if (threw(vector.result)) {
                assertFailsWith<GtvException>(what) { gsonCompatible.decodeFromString(vector.input) }
            } else {
                assertEquals(vector.result, gsonCompatible.decodeFromString(vector.input).toString(), what)
            }
        }
    }

    @Test
    fun gsonCompatibilityIsOptIn() {
        // Only that it rejects: a syntax error in strict mode comes from the underlying JSON library, and the
        // two libraries name theirs differently.
        val codec = codec(GtvJsonConfig.Default)
        for (input in GSON_ONLY_SYNTAX) {
            assertFailsWith<Throwable>("strict mode must reject <<$input>>") { codec.decodeFromString(input) }
        }
    }

    @Test
    fun gsonCompatibilityDoesNotChangeEncoding() {
        val gtv = gtv(
            "a<b" to GtvString("it's"),
            "n" to GtvNull,
            "arr" to gtv(GtvInteger(1), GtvString("2")),
        )
        assertEquals(codec(GtvJsonConfig.Default).encodeToString(gtv), gsonCompatible.encodeToString(gtv))
    }

    @Test
    fun gsonCompatibilityAppliesToEveryIoForm() {
        val input = "{y:'hi'}"
        val expected = gtv("y" to GtvString("hi"))
        assertEquals(expected, gsonCompatible.decodeFromString(input))
        assertEquals(expected, gsonCompatible.decodeFromByteArray(input.encodeToByteArray()))
        assertEquals(expected, gsonCompatible.decodeFrom(ByteArrayInputStream(input.encodeToByteArray())))
    }

    @Test
    fun gsonCompatibilityStillRejectsWhatGsonRejects() {
        for (input in listOf("", " ", "1 2", "[1 2]", "{\"a\":}", "{a b:1}", "[", "{", "\"unterminated", "/*c")) {
            assertFailsWith<GtvException>("<<$input>> must be rejected") { gsonCompatible.decodeFromString(input) }
        }
    }

    @Test
    fun bigIntegerNeedsOptingIn() {
        val value = GtvBigInteger(BigInteger("123456789012345678901234567890"))
        assertFailsWith<GtvException> { codec(GtvJsonConfig.Default).encodeToString(value) }
        assertEquals(
            "\"123456789012345678901234567890\"",
            codec(GtvJsonConfig.Strict).encodeToString(value),
        )
        assertEquals(
            "123456789012345678901234567890",
            codec(GtvJsonConfig.Lenient).encodeToString(value),
        )
    }

    @Test
    fun byteArraysBecomeHexStrings() {
        assertEquals(
            "\"0A0B\"",
            codec(GtvJsonConfig.Default).encodeToString(GtvByteArray(byteArrayOf(0x0A, 0x0B))),
        )
    }

    @Test
    fun escapesHtmlSignificantCharactersByDefault() {
        // Matches Gson, which escapes these five by default. Verified against postchain-gtv 3.49.18.
        val gtv = GtvString("a<b>c&d=e'f")
        assertEquals(
            "\"a\\u003cb\\u003ec\\u0026d\\u003de\\u0027f\"",
            codec(GtvJsonConfig.Default).encodeToString(gtv),
        )
        // Gson leaves the double quote and the slash alone, and so does this.
        assertEquals("\"a\\\"b/c\"", codec(GtvJsonConfig.Default).encodeToString(GtvString("a\"b/c")))
    }

    @Test
    fun htmlEscapingCanBeTurnedOff() {
        val gtv = GtvString("a<b>c&d=e'f")
        assertEquals("\"a<b>c&d=e'f\"", codec(GtvJsonConfig(htmlSafe = false)).encodeToString(gtv))
    }

    @Test
    fun prettyPrintsEmptyContainersInline() {
        val codec = codec(GtvJsonConfig.Default, prettyPrint = true)
        assertEquals("[]", codec.encodeToString(GtvArray(arrayOf())))
        assertEquals("{}", codec.encodeToString(GtvDictionary.build(mapOf())))
    }

    private companion object {
        /** Input Gson accepts and JSON does not, so a codec must take it only when asked to. */
        val GSON_ONLY_SYNTAX = listOf(
            "{'x':123}",
            "{y:'hi'}",
            "{y:bye}",
            "{a=1}",
            "{a=>1}",
            "'hi'",
            "bye",
            "12EF",
            "[1;2]",
            "[1,]",
            "//c\nx",
            "#c\nx",
            "/*c*/x",
            ")]}'\nx",
            "TRUE",
        )
    }
}
