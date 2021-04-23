// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.core.ProgrammerMistake
import net.postchain.gtv.messages.RawGtv
import java.io.ByteArrayInputStream

object GtvDecoder {

    fun decodeGtv(b: ByteArray): Gtv {
        val byteArray = ByteArrayInputStream(b)
        val gtv = RawGtv()
        gtv.decode(byteArray)
        return wrapValue(gtv)
    }

    private fun wrapValue(r: RawGtv): Gtv {
        if (r.null_ != null) {
            return GtvNull
        }
        if (r.integer != null) {
            return GtvInteger(r.integer.value)
        }
        if (r.string != null ) {
            return GtvString(r.string.toString())
        }
        if (r.byteArray != null) {
            return GtvByteArray(r.byteArray.value)
        }
        if (r.array != null) {
            return GtvArray((r.array.seqOf.map { wrapValue(it) }).toTypedArray())
        }
        if (r.dict != null) {
            return GtvDictionary.build(r.dict.seqOf.map { it.name.toString() to wrapValue(it.value)}.toMap())
        }
        throw ProgrammerMistake("Unknown type identifier")
    }
}