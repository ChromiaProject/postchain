// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.gtvmessages.RawGtv
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketException
import javax.net.ssl.SSLException

object GtvDecoder {

    private const val MAX_GTV_SIZE = 30_000_000
    private const val MAX_BER_DEPTH = 1_000

    fun decodeGtv(b: ByteArray): Gtv {
        val stream = ByteArrayInputStream(b)
        val result = decodeGtv(stream)
        if (stream.read() != -1) throw GtvException("Extra data at end of GTV")
        return result
    }

    fun decodeGtv(inputStream: InputStream): Gtv = try {
        decodeGtvBytes(readFirstGtv(inputStream))
    } catch (e: GtvException) {
        throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: SocketException) {
        throw e
    } catch (e: SSLException) {
        throw e
    } catch (e: IOException) {
        throw GtvException(e.message ?: "")
    }

    private fun decodeGtvBytes(b: ByteArray): Gtv {
        val gtv = RawGtv()
        gtv.decode(ByteArrayInputStream(b))
        return fromRawGtv(gtv)
    }

    /**
     * Reads exactly one BER TLV element, validating every definite length against the bytes that
     * are actually available before the underlying ASN.1 decoder can allocate for it.
     */
    private fun readFirstGtv(inputStream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val reader = BerTlvReader(inputStream, out)
        val size = reader.readElement(0)
        if (size > MAX_GTV_SIZE) throw GtvException("GTV size $size exceeds maximum $MAX_GTV_SIZE")
        return out.toByteArray()
    }

    private class BerTlvReader(
            private val input: InputStream,
            private val out: ByteArrayOutputStream
    ) {
        private var totalSize = 0

        fun readElement(depth: Int): Int {
            if (depth > MAX_BER_DEPTH) throw GtvException("GTV nesting exceeds maximum depth $MAX_BER_DEPTH")
            val startSize = totalSize
            val firstTagByte = readByte()
            var tagByte = firstTagByte.toInt() and 0xFF
            out.write(firstTagByte.toInt())

            if ((tagByte and 0x1F) == 0x1F) {
                var continuation = true
                while (continuation) {
                    val b = readByte()
                    out.write(b.toInt())
                    tagByte = b.toInt() and 0xFF
                    continuation = (tagByte and 0x80) != 0
                    if (continuation) tagByte = b.toInt() and 0x7F
                }
            }

            val lengthBytes = readLengthBytes()
            lengthBytes.forEach { out.write(it.toInt()) }
            val contentLength = decodeLength(lengthBytes)
            val constructed = (firstTagByte.toInt() and 0x20) != 0

            if (constructed) {
                var remaining = contentLength
                while (remaining > 0) {
                    val childStart = totalSize
                    readElement(depth + 1)
                    val childSize = totalSize - childStart
                    if (childSize <= 0 || childSize > remaining) {
                        throw GtvException("Constructed GTV element exceeds its declared length")
                    }
                    remaining -= childSize
                }
            } else {
                copyBytes(contentLength)
            }

            val elementSize = totalSize - startSize
            if (elementSize > MAX_GTV_SIZE) throw GtvException("GTV size exceeds maximum $MAX_GTV_SIZE")
            return elementSize
        }

        private fun readByte(): Byte {
            val value = input.read()
            if (value == -1) throw GtvException("Unexpected end of GTV")
            totalSize++
            if (totalSize > MAX_GTV_SIZE) throw GtvException("GTV size exceeds maximum $MAX_GTV_SIZE")
            return value.toByte()
        }

        private fun readLengthBytes(): ByteArray {
            val first = readByte().toInt() and 0xFF
            if (first == 0x80) throw GtvException("Indefinite length GTV elements are not accepted")
            if (first < 0x80) return byteArrayOf(first.toByte())

            val lengthByteCount = first and 0x7F
            if (lengthByteCount == 0 || lengthByteCount > 4) {
                throw GtvException("Unsupported GTV length encoding")
            }
            val result = ByteArray(lengthByteCount + 1)
            result[0] = first.toByte()
            var value = 0L
            for (i in 1..lengthByteCount) {
                val b = readByte()
                result[i] = b
                value = (value shl 8) or (b.toLong() and 0xFF)
            }
            if (value > Int.MAX_VALUE) throw GtvException("GTV element length exceeds maximum supported size")
            return result
        }

        private fun decodeLength(lengthBytes: ByteArray): Int {
            val first = lengthBytes[0].toInt() and 0xFF
            if (first < 0x80) return first
            var value = 0L
            for (i in 1 until lengthBytes.size) {
                value = (value shl 8) or (lengthBytes[i].toLong() and 0xFF)
            }
            if (value > Int.MAX_VALUE) throw GtvException("GTV element length exceeds maximum supported size")
            return value.toInt()
        }

        private fun copyBytes(count: Int) {
            val buffer = ByteArray(minOf(count, 8192))
            var remaining = count
            while (remaining > 0) {
                val toRead = minOf(remaining, buffer.size)
                val read = input.read(buffer, 0, toRead)
                if (read == -1) throw GtvException("Unexpected end of GTV")
                totalSize += read
                if (totalSize > MAX_GTV_SIZE) throw GtvException("GTV size exceeds maximum $MAX_GTV_SIZE")
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    fun fromRawGtv(r: RawGtv): Gtv {
        if (r.null_ != null) {
            return GtvNull
        }
        if (r.integer != null) {
            return GtvInteger(r.integer.value.longValueExact())
        }
        if (r.bigInteger != null) {
            return GtvBigInteger(r.bigInteger.value)
        }
        if (r.string != null ) {
            return GtvString(r.string.toString())
        }
        if (r.byteArray != null) {
            return GtvByteArray(r.byteArray.value)
        }
        if (r.array != null) {
            return GtvArray((r.array.seqOf.map { fromRawGtv(it) }).toTypedArray())
        }
        if (r.dict != null) {
            return GtvDictionary.build(r.dict.seqOf.associate { it.name.toString() to fromRawGtv(it.value) })
        }
        throw GtvException("Unknown type identifier")
    }
}
