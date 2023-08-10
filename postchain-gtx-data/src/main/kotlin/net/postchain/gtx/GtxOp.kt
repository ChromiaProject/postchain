package net.postchain.gtx

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvString
import net.postchain.gtv.mapper.ToGtv
import net.postchain.gtx.data.OpData

class GtxOp(val opName: String, vararg val args: Gtv) : ToGtv {
    private val opData = OpData(opName, args)

    /**
     * Elements are structured like an ordered array with elements:
     * 1. Operation name [GtvString]
     * 2. array of arguments [GtvArray]
     */
    override fun toGtv() = gtv(gtv(opName), gtv(args.toList()))

    fun asOpData() = opData

    fun calcSize(): Int = GtvEncoder.encodeGtv(toGtv()).size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GtxOp

        if (opName != other.opName) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = opName.hashCode()
        result = 31 * result + args.contentDeepHashCode()
        return result
    }

    companion object {
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
