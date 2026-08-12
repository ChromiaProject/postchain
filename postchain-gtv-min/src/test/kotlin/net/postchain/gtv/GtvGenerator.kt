package net.postchain.gtv

import java.math.BigInteger
import kotlin.random.Random

/**
 * Generates [Gtv] values, deliberately biased towards the cases codecs go wrong on rather than uniformly
 * random: uniform random data almost never lands on a byte-width boundary, a surrogate pair or an empty
 * collection.
 *
 * Shipped in this module's test jar so that every codec can be held to the same generated inputs.
 */
class GtvGenerator(private val random: Random) {
    fun next(depth: Int = 0): Gtv = when (random.nextInt(if (depth >= 3) 5 else 7)) {
        0 -> GtvNull
        1 -> GtvInteger(nextLong())
        2 -> GtvBigInteger(nextBigInteger())
        3 -> GtvString(nextString())
        4 -> GtvByteArray(nextBytes())
        5 -> GtvArray(Array(random.nextInt(0, 6)) { next(depth + 1) })
        else -> GtvDictionary.build(
            (0 until random.nextInt(0, 6)).associate { nextDictKey() to next(depth + 1) }
        )
    }

    /** A value with no `big_integer` anywhere, for the configurations that refuse to write one. */
    fun nextWithoutBigInteger(depth: Int = 0): Gtv = when (random.nextInt(if (depth >= 3) 4 else 6)) {
        0 -> GtvNull
        1 -> GtvInteger(nextLong())
        2 -> GtvString(nextString())
        3 -> GtvByteArray(nextBytes())
        4 -> GtvArray(Array(random.nextInt(0, 6)) { nextWithoutBigInteger(depth + 1) })
        else -> GtvDictionary.build(
            (0 until random.nextInt(0, 6)).associate { nextDictKey() to nextWithoutBigInteger(depth + 1) }
        )
    }

    fun nextLong(): Long =
        if (random.nextBoolean()) INTERESTING_LONGS.random(random) else random.nextLong()

    fun nextBigInteger(): BigInteger = when (random.nextInt(4)) {
        0 -> BigInteger.valueOf(nextLong())
        1 -> BigInteger.ONE.shiftLeft(random.nextInt(1, 300)).let { if (random.nextBoolean()) it else it.negate() }
        2 -> BigInteger.ONE.shiftLeft(random.nextInt(1, 300)).subtract(BigInteger.ONE)
            .let { if (random.nextBoolean()) it else it.negate() }

        else -> BigInteger(random.nextInt(1, 40) * 8, java.util.Random(random.nextLong()))
            .let { if (random.nextBoolean()) it else it.negate() }
    }

    fun nextString(): String = when (random.nextInt(3)) {
        0 -> INTERESTING_STRINGS.random(random)
        1 -> buildString { repeat(random.nextInt(0, 12)) { append(nextInterestingChar()) } }
        else -> buildString { repeat(random.nextInt(0, 40)) { append(('a' + random.nextInt(26))) } }
    }

    /** Dict keys are written as JSON field names, so they need the same nastiness as values. */
    fun nextDictKey(): String = when (random.nextInt(3)) {
        0 -> INTERESTING_STRINGS.random(random)
        1 -> buildString { repeat(random.nextInt(0, 8)) { append(nextInterestingChar()) } }
        else -> buildString { repeat(random.nextInt(1, 8)) { append(('a' + random.nextInt(4))) } }
    }

    fun nextBytes(): ByteArray = when (random.nextInt(3)) {
        0 -> ByteArray(0)
        1 -> ByteArray(random.nextInt(1, 40)) { random.nextInt().toByte() }
        else -> ByteArray(random.nextInt(1, 5)) { (if (random.nextBoolean()) 0x00 else 0xFF).toByte() }
    }

    private fun nextInterestingChar(): Char = when (random.nextInt(6)) {
        0 -> random.nextInt(0x00, 0x20).toChar()
        1 -> random.nextInt(0x20, 0x7f).toChar()
        2 -> random.nextInt(0x80, 0x800).toChar()
        3 -> random.nextInt(0xFFE0, 0x10000).toChar()
        4 -> LINE_SEPARATORS.random(random)
        else -> random.nextInt(0x4E00, 0x9FFF).toChar()
    }

    private companion object {
        /** Values that have historically been where codecs go wrong: sign flips, byte-width boundaries. */
        val INTERESTING_LONGS = longArrayOf(
            0, 1, -1, 2, -2, 10, -10,
            127, 128, 129, -127, -128, -129,
            255, 256, 257, -255, -256, -257,
            32767, 32768, -32768, -32769,
            65535, -65535, 65536, -65536,
            Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong(),
            Long.MAX_VALUE, Long.MIN_VALUE,
        )

        /**
         * U+2028 and U+2029 are legal in a JSON string but not in a JavaScript one, so Gson escapes them to
         * keep its output safe to paste into a script tag. Most JSON writers do not, which makes these two
         * characters the sharpest test of whether two codecs really write the same bytes.
         */
        val LINE_SEPARATORS = charArrayOf('\u2028', '\u2029')

        val INTERESTING_STRINGS = arrayOf(
            "", "a", "postchain", "R\u00e4ksm\u00f6rg\u00e5s!",
            " ", "\u00a0", "\u2028", "\u2029",
            "\n", "\t", "\r", "\b", "\\", "\"", "'", "/",
            "<", ">", "&", "=", "<a href=\"x\">&amp;</a>",
            "a\u2028b", "a\u2029b", "a\u0000b", "\u0001\u001f",
            "\ud83d\ude00", "\u97f3", "\uffef", "\ufff0",
            "0041", "00e5", "\\u0041",
            "0123456789abcdef", "x\"deadbeef\"",
            " leading and trailing ",
        )
    }
}
