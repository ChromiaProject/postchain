// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.gtvmessages.RawGtv
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketException
import javax.net.ssl.SSLException

object GtvDecoder {

    fun decodeGtv(b: ByteArray): Gtv {
        val byteArray = ByteArrayInputStream(b)
        return decodeGtv(byteArray)
    }

    fun decodeGtv(inputStream: InputStream): Gtv = try {
        val gtv = RawGtv()
        gtv.decode(inputStream)
        fromRawGtv(gtv)
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
