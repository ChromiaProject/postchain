package net.postchain.client.cli

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import kotlin.text.Typography.quote
import net.postchain.gtv.parse.GtvParser

@Deprecated("Use GtvParser.parse in stead")
fun encodeArg(arg: String): Gtv {
    return GtvParser.parse(arg)
}
