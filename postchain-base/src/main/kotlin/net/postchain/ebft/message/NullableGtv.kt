package net.postchain.ebft.message

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull

object NullableGtv {

    fun gtvToNullableByteArray(gtv: Gtv?): ByteArray? = if (gtv == null || gtv.isNull()) null else gtv.asByteArray()

    fun nullableByteArrayToGtv(value: ByteArray?): Gtv = if (value == null) GtvNull else GtvFactory.gtv(value)
}