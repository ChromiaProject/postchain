package net.postchain.eif

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvInteger
import java.lang.IllegalArgumentException

object SimpleGtvEncoder {

    /**
     * encode gtv array with assumption that the data contains only byte32 and uint256
     * that will easily to work with on ethereum solidity contract
     *
     * @param v gtv array
     *
     * Return bytearray as concatenation of 32 bytes element each
     */
    fun encodeGtv(v: Gtv): ByteArray {
        if (v !is GtvArray) throw IllegalArgumentException("input data should be an array")
        val a = v.asArray()
        var out = ByteArray(0){0}
        a.forEach {
            out = when (it) {
                is GtvArray -> {
                    val b = it.asArray()
                    b.forEach { ba ->
                        if (ba.asByteArray().size != 32) throw IllegalArgumentException("invalid byte array length")
                        out = out.plus(ba.asByteArray())
                    }
                    out
                }
                is GtvByteArray -> {
                    if (it.bytearray.size != 32) {
                        throw IllegalArgumentException("invalid byte array length")
                    }
                    out.plus(it.bytearray)
                }
                is GtvInteger -> {
                    val num = it.asBigInteger().toString(16).padStart(64, '0').hexStringToByteArray()
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