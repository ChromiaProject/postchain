package net.postchain.gtx

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.gtv.*
import net.postchain.gtv.gtxmessages.RawGtxOp

class GtxOperation(val name: String, vararg val args: Gtv) {

    internal fun asn() = RawGtxOp(BerUTF8String(name), RawGtxOp.Args(args.map { it.getRawGtv() }))

    fun gtv() = GtvFactory.gtv(GtvFactory.gtv(name), GtvFactory.gtv(args.toList()))

    companion object {
        @JvmStatic
        internal fun fromAsn(op: RawGtxOp): GtxOperation {
            return GtxOperation(op.name.toString(), *op.args.seqOf.map { GtvDecoder.fromRawGtv(it) }.toTypedArray())
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