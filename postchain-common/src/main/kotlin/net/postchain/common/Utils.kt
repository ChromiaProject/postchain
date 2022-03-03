// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.common

import java.net.ServerSocket

private val HEX_CHARS = "0123456789ABCDEF"

fun String.hexStringToByteArray(): ByteArray {
    require(length % 2 == 0) { "Invalid hex string: length is not an even number" }

    val result = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i], ignoreCase = true)
        require(firstIndex != -1) { "Char ${this[i]} is not a hex digit" }

        val secondIndex = HEX_CHARS.indexOf(this[i + 1], ignoreCase = true)
        require(secondIndex != -1) { "Char ${this[i + 1]} is not a hex digit" }

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}


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

object Utils {

    fun findFreePort(): Int {
        return ServerSocket(0).use {
            it.reuseAddress = true
            it.localPort
        }
    }
}