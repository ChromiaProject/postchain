package net.postchain.gtv

import net.postchain.common.hexStringToByteArray

public data class GtvString(val string: String) : GtvPrimitive() {
    override val type: GtvType
        get() = GtvType.STRING

    override fun asString(): String = string

    override fun asByteArray(convert: Boolean): ByteArray = if (convert) {
        try {
            string.hexStringToByteArray()
        } catch (e: IllegalArgumentException) {
            throw GtvTypeException("Can't create ByteArray from string '$string'", e)
        }
    } else {
        super.asByteArray(false)
    }

    override fun asPrimitive(): Any = string

    override fun nrOfBytes(): Int = string.length * 2

    override fun shortString(): String =
        "\"" + escapeGtv(string.take(64)) + if (string.length > 64) "..." else "\""

    override fun toString(): String = "\"${escapeGtv(string)}\""
}
