package net.postchain.gtx

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtxmessages.GTXOperation

class GtxOperation(val name: String, vararg val args: Gtv) {

    fun asn() = GTXOperation(BerUTF8String(name), GTXOperation.Args(args.map { it.getRawGtv() }))

    fun gtv() = GtvFactory.gtv(GtvFactory.gtv(name), GtvFactory.gtv(args.toList()))

}