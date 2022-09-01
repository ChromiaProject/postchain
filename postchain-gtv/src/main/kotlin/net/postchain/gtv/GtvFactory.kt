// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.common.BlockchainRid
import net.postchain.gtv.gtvmessages.DictPair
import net.postchain.gtv.gtvmessages.RawGtv
import java.math.BigInteger

fun Boolean.toLong() = if (this) 1L else 0L

/**
 * Responsible for creating various forms of GTV objects.
 */
object GtvFactory {

    // helper methods:
    fun gtv(l: Long): GtvInteger {
        return GtvInteger(l)
    }

    fun gtv(i: BigInteger): GtvBigInteger {
        return GtvBigInteger(i)
    }

    fun gtv(b: Boolean): GtvInteger {
        return GtvInteger(b.toLong())
    }

    fun gtv(s: String): GtvString {
        return GtvString(s)
    }

    fun gtv(ba: ByteArray): GtvByteArray {
        return GtvByteArray(ba)
    }

    fun gtv(ba: BlockchainRid): GtvByteArray {
        return GtvByteArray(ba.data)
    }

    fun gtv(vararg a: Gtv): GtvArray {
        return GtvArray(a)
    }

    fun gtv(a: List<Gtv>): GtvArray {
        return GtvArray(a.toTypedArray())
    }

    fun gtv(vararg pairs: Pair<String, Gtv>): GtvDictionary {
        return GtvDictionary.build(mapOf(*pairs))
    }

    fun gtv(dict: Map<String, Gtv>): GtvDictionary {
        return GtvDictionary.build(dict)
    }

    fun decodeGtv(b: ByteArray): Gtv {
        return GtvDecoder.decodeGtv(b)
    }

    fun makeDictPair(name: String, value: RawGtv): DictPair {
        val dp = DictPair()
        dp.name = BerUTF8String(name)
        dp.value = value
        return dp
    }
}