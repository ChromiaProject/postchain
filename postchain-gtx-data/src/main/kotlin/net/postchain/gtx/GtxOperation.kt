package net.postchain.gtx

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvString
import net.postchain.gtv.gtxmessages.GTXOperation

class GtxOperation(val name: String, vararg val args: Gtv) {

    fun asn() = GTXOperation(BerUTF8String(name), GTXOperation.Args(args.map { it.getRawGtv() }))

    fun gtv() = GtvFactory.gtv(GtvFactory.gtv(name), GtvFactory.gtv(args.toList()))

    companion object {
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