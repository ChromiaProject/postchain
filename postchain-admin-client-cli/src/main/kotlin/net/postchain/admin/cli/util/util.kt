@file:Suppress("DuplicatedCode")

package net.postchain.admin.cli.util

// This is duplicated from postchain-common/src/main/kotlin/net/postchain/common/Utils.kt to avoid dependency on postchain-common

private const val HEX_CHARS = "0123456789ABCDEF"

private val HEX_CHAR_ARRAY = HEX_CHARS.toCharArray()

fun ByteArray.toHex(): String {
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHAR_ARRAY[firstIndex])
        result.append(HEX_CHAR_ARRAY[secondIndex])
    }

    return result.toString()
}
