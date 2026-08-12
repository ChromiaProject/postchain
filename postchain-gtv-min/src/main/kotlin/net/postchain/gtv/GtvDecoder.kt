package net.postchain.gtv

import net.postchain.gtv.internal.DerReader
import net.postchain.gtv.internal.Tags
import net.postchain.gtv.internal.derContentToLong
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.math.BigInteger
import java.net.SocketException
import javax.net.ssl.SSLException

public object GtvDecoder {
    public fun decodeGtv(b: ByteArray): Gtv {
        val reader = DerReader(b)
        val gtv = decodeGtv(reader)
        if (!reader.exhausted) throw GtvException("Extra data at end of GTV")
        return gtv
    }

    /**
     * Decodes the next value on [inputStream], leaving anything that follows it unread.
     *
     * A transport failure is not a malformed value, so [InterruptedIOException], [SocketException] and
     * [SSLException] pass through while every other [IOException] becomes a [GtvException].
     */
    public fun decodeGtv(inputStream: InputStream): Gtv = try {
        decodeGtv(DerReader(inputStream))
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

    private fun decodeGtv(reader: DerReader): Gtv {
        val tag = reader.readTag()
        // The explicit-tag wrapper's length is consumed but deliberately not enforced. jasn1, which used to
        // decode GTV here, reads it and then decodes the component without checking that it fits, so
        // `A0000500` and `A0800500` both decode to GtvNull. A decoder that rejected bytes an older node
        // accepts would fork the chain, so this one is lenient in exactly the same place.
        reader.readLength()

        val gtv = when (tag) {
            Tags.CHOICE_NULL -> {
                val content = reader.readTlv(Tags.NULL)
                if (content.isNotEmpty()) throw GtvException("ASN.1 NULL must have empty content")
                GtvNull
            }

            Tags.CHOICE_BYTEARRAY -> GtvByteArray(reader.readTlv(Tags.OCTET_STRING))

            Tags.CHOICE_STRING -> GtvString(reader.readTlv(Tags.UTF8_STRING).decodeToString())

            Tags.CHOICE_INTEGER -> GtvInteger(derContentToLong(reader.readTlv(Tags.INTEGER)))

            Tags.CHOICE_BIGINTEGER -> GtvBigInteger(BigInteger(reader.readTlv(Tags.INTEGER)))

            Tags.CHOICE_ARRAY -> {
                val elements = mutableListOf<Gtv>()
                readSequence(reader) { elements.add(decodeGtv(reader)) }
                GtvArray(elements.toTypedArray())
            }

            Tags.CHOICE_DICT -> {
                val pairs = mutableMapOf<String, Gtv>()
                readSequence(reader) {
                    reader.expectTag(Tags.SEQUENCE)
                    val pairLength = reader.readLength()
                    if (pairLength == DerReader.INDEFINITE) {
                        throw GtvException("Indefinite length is not supported for SEQUENCE")
                    }
                    val pairEnd = reader.endOf(pairLength)
                    val key = reader.readTlv(Tags.UTF8_STRING).decodeToString()
                    pairs[key] = decodeGtv(reader)
                    reader.checkAt(pairEnd)
                }
                GtvDictionary.build(pairs)
            }

            else -> throw GtvException("Unknown type identifier")
        }

        return gtv
    }

    private inline fun readSequence(reader: DerReader, readElement: () -> Unit) {
        reader.expectTag(Tags.SEQUENCE)
        val length = reader.readLength()
        // Unlike the explicit-tag wrapper above, jasn1 rejects the indefinite form on a SEQUENCE, so this does
        // too. Both halves of that asymmetry matter: `A0800500` must be accepted, `A5023080` must be
        // rejected.
        if (length == DerReader.INDEFINITE) throw GtvException("Indefinite length is not supported for SEQUENCE")
        val end = reader.endOf(length)
        while (reader.positionBefore(end)) readElement()
        reader.checkAt(end)
    }
}
