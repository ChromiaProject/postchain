package net.postchain.gtv.internal

import net.postchain.gtv.GtvException
import java.io.InputStream

/**
 * The slice of ASN.1 DER that the GTV schema needs.
 *
 * GTV's `RawGtv` is a `CHOICE` with context tags `[0]`..`[6]`, and the schema uses the ASN.1 default of
 * explicit tagging, so every value is a constructed context-specific TLV wrapping one universal TLV:
 *
 * ```
 * A3 03 02 01 2A   -- [3] { INTEGER 42 }
 * ```
 */
internal object Tags {
    const val NULL: Byte = 0x05
    const val OCTET_STRING: Byte = 0x04
    const val UTF8_STRING: Byte = 0x0C
    const val INTEGER: Byte = 0x02
    const val SEQUENCE: Byte = 0x30

    const val CHOICE_NULL: Byte = 0xA0.toByte()
    const val CHOICE_BYTEARRAY: Byte = 0xA1.toByte()
    const val CHOICE_STRING: Byte = 0xA2.toByte()
    const val CHOICE_INTEGER: Byte = 0xA3.toByte()
    const val CHOICE_DICT: Byte = 0xA4.toByte()
    const val CHOICE_ARRAY: Byte = 0xA5.toByte()
    const val CHOICE_BIGINTEGER: Byte = 0xA6.toByte()
}

/**
 * Grows backwards, which is what DER wants: a definite length can only be written once the content it
 * measures already exists. Writing right-to-left means every element is emitted exactly once, with no
 * intermediate buffers per nesting level.
 */
internal class ReverseBuffer(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)
    private var start = buffer.size

    val size: Int get() = buffer.size - start

    private fun reserve(n: Int) {
        if (start >= n) return
        val needed = size + n
        var newCapacity = buffer.size * 2
        while (newCapacity < needed * 2) newCapacity *= 2
        val grown = ByteArray(newCapacity)
        val newStart = newCapacity - size
        buffer.copyInto(grown, newStart, start, buffer.size)
        buffer = grown
        start = newStart
    }

    fun writeByte(b: Byte) {
        reserve(1)
        buffer[--start] = b
    }

    fun writeBytes(bytes: ByteArray) {
        reserve(bytes.size)
        start -= bytes.size
        bytes.copyInto(buffer, start)
    }

    /** DER definite length, shortest form. */
    fun writeLength(length: Int) {
        if (length < 0x80) {
            writeByte(length.toByte())
            return
        }
        var remaining = length
        var count = 0
        while (remaining > 0) {
            writeByte(remaining.toByte())
            remaining = remaining ushr 8
            count++
        }
        writeByte((0x80 or count).toByte())
    }

    fun toByteArray(): ByteArray = buffer.copyOfRange(start, buffer.size)
}

/** The minimal signed big-endian representation of [value], as ASN.1 INTEGER content. */
internal fun longToDerContent(value: Long): ByteArray {
    var n = 8
    while (n > 1) {
        val top = ((value shr ((n - 1) * 8)) and 0xFF).toInt()
        val nextMostSignificantBit = ((value shr ((n - 2) * 8 + 7)) and 1L).toInt()
        val redundant = (top == 0x00 && nextMostSignificantBit == 0) || (top == 0xFF && nextMostSignificantBit == 1)
        if (!redundant) break
        n--
    }
    return ByteArray(n) { i -> (value shr ((n - 1 - i) * 8)).toByte() }
}

/** Reads ASN.1 INTEGER content as a [Long], rejecting anything that does not fit. */
internal fun derContentToLong(content: ByteArray): Long {
    if (content.isEmpty()) throw GtvException("Empty ASN.1 INTEGER")
    if (content.size > 8) throw GtvException("ASN.1 INTEGER too large for a 64-bit integer: ${content.size} bytes")

    return content.fold(if (content[0].toInt() < 0) -1L else 0L) { acc, b ->
        (acc shl 8) or (b.toLong() and 0xFF)
    }
}

/**
 * Cursor over DER-encoded bytes, either a complete buffer or an [InputStream] the bytes arrive on.
 *
 * The stream form never reads further ahead than the value being decoded needs, so anything following that
 * value is left on the stream. jasn1 behaves the same way and `GtvDecoder.decodeGtv` relies on it.
 */
internal class DerReader private constructor(
    private var bytes: ByteArray,
    private var limit: Int,
    private val source: InputStream?,
) {
    constructor(bytes: ByteArray) : this(bytes, bytes.size, null)

    constructor(source: InputStream) : this(ByteArray(STREAM_BUFFER), 0, source)

    private var position = 0

    val exhausted: Boolean get() = position >= limit

    /** Makes [count] more bytes readable, pulling exactly that many from [source] if there is one. */
    private fun ensure(count: Int) {
        if (count >= 0 && position.toLong() + count <= limit) return
        val stream = source
        if (count < 0 || stream == null) throw GtvException("Unexpected end of GTV input")

        val missing = (position.toLong() + count - limit).toInt()
        if (limit.toLong() + missing > bytes.size) {
            bytes = bytes.copyOf(maxOf(bytes.size * 2L, limit.toLong() + missing).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        var read = 0
        while (read < missing) {
            val n = stream.read(bytes, limit + read, missing - read)
            if (n < 0) throw GtvException("Unexpected end of GTV input")
            read += n
        }
        limit += missing
    }

    fun readTag(): Byte {
        ensure(1)
        return bytes[position++]
    }

    fun expectTag(expected: Byte) {
        val actual = readTag()
        if (actual != expected) {
            throw GtvException("Expected ASN.1 tag 0x${expected.toHexByte()} but found 0x${actual.toHexByte()}")
        }
    }

    /**
     * @return the content length, or [INDEFINITE] for BER's indefinite form.
     *
     * DER forbids the indefinite form, but jasn1 accepted it, and so must this: a decoder that rejects bytes
     * an older node accepts would fork the chain. `A0800500` is the shortest input that shows it.
     */
    fun readLength(): Int {
        ensure(1)
        val first = bytes[position++].toInt() and 0xFF
        if (first < 0x80) return first
        if (first == 0x80) return INDEFINITE
        val count = first and 0x7F
        if (count > 4) throw GtvException("ASN.1 length field of $count bytes is too large")
        ensure(count)
        var length = 0
        repeat(count) {
            length = (length shl 8) or (bytes[position++].toInt() and 0xFF)
        }
        if (length < 0) throw GtvException("ASN.1 length field overflows")
        return length
    }

    fun readContent(length: Int): ByteArray {
        ensure(length)
        val slice = bytes.copyOfRange(position, position + length)
        position += length
        return slice
    }

    /** Consumes a full TLV of the given tag and returns its content. */
    fun readTlv(expected: Byte): ByteArray {
        expectTag(expected)
        return readContent(readLength())
    }

    /** Position of the byte just past a container that starts here and has [length] bytes of content. */
    fun endOf(length: Int): Int = position + length

    fun positionBefore(end: Int): Boolean = position < end

    fun checkAt(end: Int) {
        if (position != end) throw GtvException("ASN.1 container length does not match its content")
    }


    companion object {
        const val INDEFINITE: Int = -1

        private const val STREAM_BUFFER = 1024
    }
}

private fun Byte.toHexByte(): String = (toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
