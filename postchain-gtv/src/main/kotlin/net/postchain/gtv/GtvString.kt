// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.gtvmessages.RawGtv

data class GtvString(val string: String) : GtvPrimitive() {

    override val type: GtvType = GtvType.STRING

    override fun asString(): String {
        return string
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv().apply { string = BerUTF8String(this@GtvString.string) }
    }

    override fun asByteArray(convert: Boolean): ByteArray {
        try {
            return if (convert) string.hexStringToByteArray() else super.asByteArray(false)
        } catch (e: Exception) {
            throw UserMistake("Can't create ByteArray from string '$string'")
        }
    }

    override fun asPrimitive(): Any? {
        return string
    }

    override fun nrOfBytes(): Int {
        return (string.length * 2)
    }

    override fun toString(): String {
        return "\"$string\""
    }
}