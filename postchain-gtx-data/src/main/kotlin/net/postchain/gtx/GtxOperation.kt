package net.postchain.gtx

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvString
import net.postchain.gtv.gtxmessages.RawGtxOp

class GtxOperation(val name: String, vararg val args: Gtv) {

    internal fun asn() = RawGtxOp(BerUTF8String(name), gtv(*args).getRawGtv())

    /**
     * Elements are structured like an ordered array with elements:
     * 1. Operation name [GtvString]
     * 2. array of arguments [GtvArray]
     */
    fun toGtv() = gtv(gtv(name), gtv(args.toList()))

    companion object {
        @JvmStatic
        internal fun fromAsn(op: RawGtxOp): GtxOperation {
            return GtxOperation(op.name.toString(), *op.args.array.seqOf.map { GtvDecoder.fromRawGtv(it) }.toTypedArray())
        }
        @JvmStatic
        fun fromGtv(gtv: Gtv): GtxOperation {
            if ((gtv !is GtvArray) && gtv.asArray().size != 2) throw IllegalArgumentException("Gtv must be an array of size 2")
            gtv.asArray().let { array ->
                if (array[0] !is GtvString) throw IllegalArgumentException("First element must be a string")
                if (array[1] !is GtvArray) throw IllegalArgumentException("Second element must be an array")
                return GtxOperation(array[0].asString(), array[1])
            }
        }
    }
}