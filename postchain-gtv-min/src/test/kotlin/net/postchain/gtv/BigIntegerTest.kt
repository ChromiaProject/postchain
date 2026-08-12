package net.postchain.gtv

import net.postchain.common.toHex
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GTV encodes `big_integer` as a bare ASN.1 INTEGER, so its wire form is exactly
 * `java.math.BigInteger.toByteArray()`. These cases pin that down across the sign and width boundaries where an
 * encoder is most likely to be off by a byte.
 */
class BigIntegerTest {
    /** Decimal strings spanning the interesting boundaries: sign changes, byte widths, powers of two. */
    private val decimalSamples: List<String> = listOf(
        "0", "1", "-1", "2", "-2",
        "127", "128", "129", "-127", "-128", "-129", "-130",
        "255", "256", "257", "-255", "-256", "-257",
        "32767", "32768", "-32768", "-32769",
        "9223372036854775807", "-9223372036854775808",
        "9223372036854775808", "-9223372036854775809",
        "92233720368547758078", "-92233720368547758078",
        "784637716923335095224261902710254454442933591094742482943",
        "-784637716923335095224261902710254454442933591094742482943",
        "1461501637330902918203684832716283019655932542975",
    )

    /**
     * Byte patterns that exercise every width up to 33 bytes with both signs and both leading-bit cases,
     * which is where a two's-complement conversion is most likely to be off by a byte.
     */
    private fun bytePatterns(): List<ByteArray> = buildList {
        for (width in 1..33) {
            add(ByteArray(width) { 0x00 }.also { it[0] = 0x01 }) // 2^(8*width-8)
            add(ByteArray(width) { 0xFF.toByte() }) // -1

            add(ByteArray(width) { 0x7F }) // largest positive of this width
            add(ByteArray(width) { 0x00 }.also { it[0] = 0x80.toByte() }) // most negative of this width
            add(ByteArray(width) { i -> (i * 37 + 11).toByte() })
        }
    }

    @Test
    fun twosComplementRoundTripsFromDecimal() {
        for (sample in decimalSamples) {
            val value = BigInteger(sample)
            val bytes = value.toByteArray()
            assertTrue(bytes.isNotEmpty(), "empty encoding for $sample")
            assertEquals(sample, BigInteger(bytes).toString(), "round trip $sample")
        }
    }

    @Test
    fun twosComplementRoundTripsFromBytes() {
        for (bytes in bytePatterns()) {
            val value = BigInteger(bytes)
            // Re-encoding may shorten a non-minimal input, but must denote the same number and then be stable.
            val reencoded = value.toByteArray()
            assertEquals(value, BigInteger(reencoded), "byte round trip ${bytes.toHex()}")
            assertEquals(reencoded.toHex(), BigInteger(reencoded).toByteArray().toHex(), "stable ${bytes.toHex()}")
            assertEquals(value, BigInteger(value.toString()), "decimal round trip ${bytes.toHex()}")
        }
    }

    @Test
    fun twosComplementUsesMinimalLength() {
        for (sample in decimalSamples) {
            val bytes = BigInteger(sample).toByteArray()
            if (bytes.size > 1) {
                val leadingIsRedundant = (bytes[0] == 0.toByte() && bytes[1].toInt() >= 0) ||
                    (bytes[0] == 0xFF.toByte() && bytes[1].toInt() < 0)
                assertTrue(!leadingIsRedundant, "non-minimal encoding for $sample: ${bytes.toHex()}")
            }
        }
    }

    @Test
    fun signumAndBitLengthFollowJavaSemantics() {
        assertEquals(0, BigInteger("0").signum())
        assertEquals(1, BigInteger("1").signum())
        assertEquals(-1, BigInteger("-1").signum())

        assertEquals(0, BigInteger("0").bitLength())
        assertEquals(1, BigInteger("1").bitLength())
        assertEquals(0, BigInteger("-1").bitLength())
        assertEquals(7, BigInteger("127").bitLength())
        assertEquals(8, BigInteger("128").bitLength())
        assertEquals(7, BigInteger("-128").bitLength())
        assertEquals(8, BigInteger("-129").bitLength())
        assertEquals(8, BigInteger("255").bitLength())
        assertEquals(9, BigInteger("256").bitLength())
        assertEquals(8, BigInteger("-256").bitLength())
    }

    @Test
    fun comparesAndEqualsAcrossSigns() {
        assertTrue(BigInteger("-1") < BigInteger("0"))
        assertTrue(BigInteger("0") < BigInteger("1"))
        assertTrue(BigInteger("9223372036854775808") > BigInteger("9223372036854775807"))
        assertEquals(BigInteger("42"), BigInteger.valueOf(42))
        assertEquals(BigInteger("42").hashCode(), BigInteger.valueOf(42).hashCode())
    }

    @Test
    fun survivesGtvBinaryRoundTrip() {
        for (sample in decimalSamples) {
            val gtv = GtvBigInteger(BigInteger(sample))
            val decoded = GtvDecoder.decodeGtv(GtvEncoder.encodeGtv(gtv))
            assertEquals(gtv, decoded, "gtv round trip $sample")
            assertEquals(sample, decoded.asBigInteger().toString())
        }
    }

    @Test
    fun matchesKnownDerEncodings() {
        // Cross-checked against java.math.BigInteger.toByteArray() wrapped in the GTV [6] INTEGER choice.
        val expected = mapOf(
            "0" to "A603020100",
            "1" to "A603020101",
            "-1" to "A6030201FF",
            "127" to "A60302017F",
            "128" to "A60402020080",
            "-128" to "A603020180",
            "-129" to "A6040202FF7F",
            "256" to "A60402020100",
        )
        for ((decimal, der) in expected) {
            assertEquals(der, GtvEncoder.encodeGtv(GtvBigInteger(BigInteger(decimal))).toHex(), decimal)
        }
    }
}
