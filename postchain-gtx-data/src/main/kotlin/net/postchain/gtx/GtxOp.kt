package net.postchain.gtx

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvString
import net.postchain.gtv.gtxmessages.RawGtxOp
import net.postchain.gtx.data.OpData

class GtxOp(val name: String, vararg val args: Gtv) {

    internal fun toRaw() = RawGtxOp(BerUTF8String(name), RawGtxOp.Args(args.map { it.getRawGtv() }))

    /**
     * Elements are structured like an ordered array with elements:
     * 1. Operation name [GtvString]
     * 2. array of arguments [GtvArray]
     */
    fun toGtv() = gtv(gtv(name), gtv(args.toList()))
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GtxOp

        if (name != other.name) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }

    companion object {
        @JvmStatic
        internal fun fromAsn(op: RawGtxOp): GtxOp {
            return GtxOp(op.name.toString(), *op.args.seqOf.map { GtvDecoder.fromRawGtv(it) }.toTypedArray())
        }
        @JvmStatic
        fun fromGtv(gtv: Gtv): GtxOp {
            if ((gtv !is GtvArray) && gtv.asArray().size != 2) throw IllegalArgumentException("Gtv must be an array of size 2")
            gtv.asArray().let { array ->
                if (array[0] !is GtvString) throw IllegalArgumentException("First element must be a string")
                if (array[1] !is GtvArray) throw IllegalArgumentException("Second element must be an array")
                return GtxOp(array[0].asString(), *array[1].asArray())
            }
        }

        @JvmStatic
        fun fromOpData(opData: OpData) = GtxOp(opData.opName, *opData.args)
    }
}
