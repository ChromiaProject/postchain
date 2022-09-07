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

/**
 * Conceptually = all messages related to a block
 */
data class IcmfPacket(
        val currentPointer: Gtv,
        val height: Long, // Block height this package corresponds to
        val blockRid: Gtv, // The BlockRid that goes with the header (for the cases where we cannot calculate it from the header)
        val blockHeader: Gtv, // Header of the block
        val witness: Gtv // Must send the witness so the recipient can validate
) {

    companion object {
        /**
         * @return a [IcmfPackage] without messages (Anchoring can use this)
         */
        fun build(height: Long, blockRid: Gtv, header: Gtv, witness: Gtv): IcmfPacket {
            return IcmfPacket(gtv(height), height, blockRid, header, witness)
        }
    }

}