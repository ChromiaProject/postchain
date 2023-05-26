// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.gtvmessages.RawGtv
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

object GtvDecoder {

    fun decodeGtv(b: ByteArray): Gtv {
        val byteArray = ByteArrayInputStream(b)
        return try {
            decodeGtv(byteArray)
        } catch (e: GtvException) {
            throw e
        } catch (e: IOException) {
            throw GtvException(e.message ?: "")
        }
    }

    fun decodeGtv(inputStream: InputStream): Gtv {
        val gtv = RawGtv()
        gtv.decode(inputStream)
        return fromRawGtv(gtv)
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
            return GtvDictionary.build(r.dict.seqOf.map { it.name.toString() to fromRawGtv(it.value) }.toMap())
        }
        throw GtvException("Unknown type identifier")
    }
}
