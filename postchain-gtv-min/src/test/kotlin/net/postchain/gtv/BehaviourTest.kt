package net.postchain.gtv

import net.postchain.common.toHex
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.builder.GtvBuilder
import net.postchain.gtv.parse.GtvParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GtvTypeTest {

    @Test
    fun accessorsThrowForTheWrongType() {
        assertFailsWith<GtvTypeException> { GtvInteger(1).asString() }
        assertFailsWith<GtvTypeException> { GtvString("x").asInteger() }
        assertFailsWith<GtvTypeException> { GtvNull.asArray() }
        assertFailsWith<GtvTypeException> { GtvArray(arrayOf()).asDict() }
        assertFailsWith<GtvTypeException> { GtvString("zz").asByteArray(convert = true) }
    }

    @Test
    fun booleansAreIntegers() {
        assertEquals(GtvInteger(1), gtv(true))
        assertEquals(GtvInteger(0), gtv(false))
        assertTrue(GtvInteger(1).asBoolean())
        assertTrue(!GtvInteger(0).asBoolean())
    }

    @Test
    fun stringConvertsToBytesOnlyWhenAsked() {
        assertEquals("0A0B", GtvString("0a0b").asByteArray(convert = true).toHex())
        assertFailsWith<GtvTypeException> { GtvString("0a0b").asByteArray() }
    }

    @Test
    fun dictionariesAreAlwaysKeySorted() {
        val dict = GtvDictionary.build(mapOf("c" to GtvInteger(3), "a" to GtvInteger(1), "b" to GtvInteger(2)))
        assertEquals(listOf("a", "b", "c"), dict.dict.keys.toList())
        assertNull(dict["missing"])
    }

    @Test
    fun byteArraysCompareByContent() {
        assertEquals(GtvByteArray(byteArrayOf(1, 2)), GtvByteArray(byteArrayOf(1, 2)))
        assertEquals(GtvByteArray(byteArrayOf(1, 2)).hashCode(), GtvByteArray(byteArrayOf(1, 2)).hashCode())
        assertTrue(GtvByteArray(byteArrayOf(1, 2)) != GtvByteArray(byteArrayOf(1, 3)))
    }

    @Test
    fun arraysCompareByContent() {
        assertEquals(gtv(GtvInteger(1), GtvString("a")), gtv(GtvInteger(1), GtvString("a")))
        assertEquals(gtv(GtvInteger(1)).hashCode(), gtv(GtvInteger(1)).hashCode())
    }
}

class GtvDecoderErrorTest {

    @Test
    fun rejectsTrailingData() {
        val encoded = GtvEncoder.encodeGtv(GtvInteger(1))
        assertFailsWith<GtvException> { GtvDecoder.decodeGtv(encoded + 0) }
    }

    @Test
    fun rejectsTruncatedInput() {
        val encoded = GtvEncoder.encodeGtv(gtv(GtvInteger(1), GtvString("hello")))
        for (cut in 1 until encoded.size) {
            assertFailsWith<GtvException>("truncated at $cut") {
                GtvDecoder.decodeGtv(encoded.copyOfRange(0, cut))
            }
        }
    }

    @Test
    fun rejectsUnknownChoiceTag() {
        assertFailsWith<GtvException> { GtvDecoder.decodeGtv(byteArrayOf(0xA7.toByte(), 2, 5, 0)) }
    }

    @Test
    fun toleratesIndefiniteLengthOnTheChoiceWrapperButNotOnSequence() {
        // Bug-compatible with jasn1, which reads the explicit-tag wrapper's length and never enforces it, so
        // both an indefinite and a wrong length decode fine there.
        assertEquals(GtvNull, GtvDecoder.decodeGtv(byteArrayOf(0xA0.toByte(), 0x80.toByte(), 5, 0)))
        assertEquals(GtvNull, GtvDecoder.decodeGtv(byteArrayOf(0xA0.toByte(), 0x00, 5, 0)))

        // On a SEQUENCE it does reject the indefinite form. Rejecting what postchain accepts, or accepting what
        // it rejects, would fork a chain — so both halves of this asymmetry are pinned.
        assertFailsWith<GtvException> { GtvDecoder.decodeGtv(byteArrayOf(0xA5.toByte(), 2, 0x30, 0x80.toByte())) }
    }

    @Test
    fun rejectsIntegersWiderThanLong() {
        // [3] { INTEGER of 9 bytes } — valid DER, but GtvInteger is a Long.
        val tooWide = byteArrayOf(0xA3.toByte(), 11, 2, 9, 1, 0, 0, 0, 0, 0, 0, 0, 0)
        assertFailsWith<GtvException> { GtvDecoder.decodeGtv(tooWide) }
    }

    @Test
    fun rejectsEmptyInput() {
        assertFailsWith<GtvException> { GtvDecoder.decodeGtv(byteArrayOf()) }
    }
}

class GtvParserTest {

    @Test
    fun parsesEachLiteralForm() {
        assertEquals(GtvNull, GtvParser.parse("null"))
        assertEquals(GtvInteger(1), GtvParser.parse("true"))
        assertEquals(GtvInteger(0), GtvParser.parse("false"))
        assertEquals(GtvInteger(-42), GtvParser.parse("-42"))
        assertEquals(GtvString("hi"), GtvParser.parse("\"hi\""))
        assertEquals(GtvString("hi"), GtvParser.parse("'hi'"))
        assertEquals(GtvByteArray(byteArrayOf(0x0A, 0x0B)), GtvParser.parse("x\"0A0B\""))
        assertEquals(GtvArray(arrayOf()), GtvParser.parse("[]"))
        assertEquals(GtvDictionary.build(mapOf()), GtvParser.parse("[:]"))
    }

    @Test
    fun parsesBigIntegerSuffix() {
        assertEquals("42L", GtvParser.parse("42L").toString())
    }

    @Test
    fun parsesNestedCollections() {
        val parsed = GtvParser.parse("""["a": [1, 2], "b": ["c": x"FF"]]""")
        assertEquals(GtvInteger(2), parsed["a"]!![1])
        assertEquals(GtvByteArray(byteArrayOf(-1)), parsed["b"]!!["c"])
    }

    @Test
    fun handlesEscapesInStrings() {
        assertEquals(GtvString("a\"b\\c\nd"), GtvParser.parse(""""a\"b\\c\nd""""))
        assertEquals(GtvString("å"), GtvParser.parse(""""å""""))
    }

    @Test
    fun rejectsTrailingTokens() {
        assertFailsWith<IllegalArgumentException> { GtvParser.parse("1 2") }
    }
}

class GtvBuilderTest {

    @Test
    fun mergesDictionariesKeepingTheNewValue() {
        val builder = GtvBuilder(gtv("a" to GtvInteger(1), "b" to GtvInteger(2)))
        builder.update(gtv("b" to GtvInteger(20), "c" to GtvInteger(3)))
        assertEquals(gtv("a" to GtvInteger(1), "b" to GtvInteger(20), "c" to GtvInteger(3)), builder.build())
    }

    @Test
    fun appendsArraysByDefault() {
        val builder = GtvBuilder(gtv("a" to gtv(GtvInteger(1))))
        builder.update(gtv("a" to gtv(GtvInteger(2))))
        assertEquals(gtv("a" to gtv(GtvInteger(1), GtvInteger(2))), builder.build())
    }

    @Test
    fun writesThroughAPath() {
        val builder = GtvBuilder()
        builder.update(GtvInteger(7), "x", "y")
        assertEquals(GtvInteger(7), builder.build()["x"]!!["y"])
    }

    @Test
    fun refusesToMergeMismatchedTypes() {
        val builder = GtvBuilder(gtv("a" to GtvInteger(1)))
        assertFailsWith<IllegalStateException> { builder.update(gtv("a" to gtv(GtvInteger(2)))) }
    }
}

/**
 * The `\uXXXX` escape in the textual notation has always been broken, and it is reproduced rather
 * than diverging.
 *
 * The lexer reads `sb.append(Integer.parseInt(consume(4)), 16)`, which looks like a misplaced
 * parenthesis for a radix argument. It compiles, but resolves to Kotlin's `StringBuilder.append(vararg Any?)`:
 * the four digits are parsed as *decimal* and appended as text, followed by a literal `16`.
 *
 * Every expectation below was observed by running postchain-gtv 3.49.18.
 */
class GtvParserEscapeTest {

    @Test
    fun reproducesTheUpstreamUnicodeEscapeBug() {
        assertEquals(GtvString("4116"), GtvParser.parse("\"\\u0041\""))
        assertEquals(GtvString("3016"), GtvParser.parse("\"\\u0030\""))
        assertEquals(GtvString("x4116y"), GtvParser.parse("\"x\\u0041y\""))
    }

    @Test
    fun rejectsHexDigitsLikeUpstream() {
        // "00e5" is not a decimal number, so Integer.parseInt throws.
        assertFailsWith<NumberFormatException> { GtvParser.parse("\"\\u00e5\"") }
    }

    @Test
    fun soToStringIsNotAlwaysReparsable() {
        // A control character escapes on the way out and comes back mangled — same as upstream.
        val original = GtvString("\u0001")
        assertEquals("\"\\u0001\"", original.toString())
        assertEquals(GtvString("116"), GtvParser.parse(original.toString()))

        // An astral character escapes to a surrogate pair, whose digits are not decimal.
        assertFailsWith<NumberFormatException> { GtvParser.parse(GtvString("\uD83D\uDE00").toString()) }
    }

    @Test
    fun theOtherEscapesAreFine() {
        assertEquals(GtvString("a\"b\\c\nd\te\rf"), GtvParser.parse("\"a\\\"b\\\\c\\nd\\te\\rf\""))
    }
}
