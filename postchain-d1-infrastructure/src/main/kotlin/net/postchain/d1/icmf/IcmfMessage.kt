// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

/**
 * Smallest message unit for ICMF
 */
data class IcmfMessage(
    val topic: String, // Topic of message
    val body: Gtv
) {

    companion object {
        fun fromGtv(gtv: Gtv): IcmfMessage {
            return IcmfMessage(gtv["topic"]!!.asString(), gtv["body"]!!)
        }
    }

    fun toGtv(): Gtv {
        return gtv("topic" to gtv(topic), "body" to body)
    }
}
