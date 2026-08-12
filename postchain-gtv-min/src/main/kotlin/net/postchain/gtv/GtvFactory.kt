package net.postchain.gtv

import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import java.math.BigInteger

public fun Boolean.toLong(): Long = if (this) 1L else 0L

/**
 * Responsible for creating various forms of GTV objects.
 */
public object GtvFactory {
    public fun gtv(l: Long): GtvInteger = GtvInteger(l)
    public fun gtv(i: BigInteger): GtvBigInteger = GtvBigInteger(i)
    public fun gtv(b: Boolean): GtvInteger = GtvInteger(b.toLong())
    public fun gtv(s: String): GtvString = GtvString(s)
    public fun gtv(ba: ByteArray): GtvByteArray = GtvByteArray(ba)
    public fun gtv(wba: WrappedByteArray): GtvByteArray = GtvByteArray(wba.data)
    public fun gtv(ba: BlockchainRid): GtvByteArray = GtvByteArray(ba.data)
    public fun gtv(vararg a: Gtv): GtvArray = GtvArray(a)
    public fun gtv(a: List<Gtv>): GtvArray = GtvArray(a.toTypedArray())
    public fun gtv(vararg pairs: Pair<String, Gtv>): GtvDictionary = GtvDictionary.build(mapOf(*pairs))
    public fun gtv(dict: Map<String, Gtv>): GtvDictionary = GtvDictionary.build(dict)
    public fun decodeGtv(b: ByteArray): Gtv = GtvDecoder.decodeGtv(b)
}
