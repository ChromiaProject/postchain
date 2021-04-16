// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.common.hexStringToByteArray
import org.openmuc.jasn1.ber.ReverseByteArrayOutputStream;
import java.lang.IllegalArgumentException

/**
 * Responsible for turning GTV objects into binary data.
 */
object GtvEncoder {
    fun encodeGtv(v: Gtv): ByteArray {
        val outs = ReverseByteArrayOutputStream(1000, true)
        v.getRawGtv().encode(outs)
        return outs.array
    }

    /**
     * encode gtv array with assumption that the data contains only byte32 and uint256
     * that will easily to work with on ethereum solidity contract
     *
     * @param v gtv array
     *
     * Return bytearray as concatenation of 32 bytes element each
     */
    fun simpleEncodeGtv(v: Gtv): ByteArray {
        // TODO: temporarily strictly data validation for ease
        if (v !is GtvArray) throw IllegalArgumentException("input data should be an array")
        val a = v.asArray()
        var out = ByteArray(0){0}
        a.forEach {
            out = when (it) {
                is GtvByteArray -> {
                    // TODO: temporarily strictly data validation for ease
                    if (it.bytearray.size != 32) {
                        throw IllegalArgumentException("invalid byte array length")
                    }
                    out.plus(it.bytearray)
                }
                is GtvInteger -> {
                    val num = it.asBigInteger().toString(16).padStart(64, '0').hexStringToByteArray()
                    // TODO: temporarily strictly data validation for ease
                    if (num.size != 32) {
                        throw IllegalArgumentException("invalid byte array length")
                    }
                    out.plus(num)
                }
                else -> {
                    throw IllegalArgumentException("input data type was not supported")
                }
            }
        }
        return out
    }
}