// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.core.BlockWitness
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull

/**
 * Smallest message unit for ICMF
 */
data class IcmfMessage(
    val height: Long,  // Block height this message corresponds to
    val messageType: String, // Type of message
    val body: Gtv ) { // Actual payload

}

/**
 * Conceptually = all messages related to a block
 */
data class IcmfPackage(
    val height: Long, // Block height this package corresponds to
    val blockRid: Gtv, // The BlockRid that goes with the header (for the cases where we cannot calculate it from the header)
    val blockHeader: Gtv, // Header of the block
    val witness: Gtv, // Must send the witness so the recipient can validate
    val messages: List<IcmfMessage> // (potentially) messages
) {

    companion object {

        /**
         * @return a [IcmfPackage] without messages (Anchoring can use this)
         */
        fun build(height: Long, blockRid: Gtv, header: Gtv, witness: Gtv): IcmfPackage {
            return IcmfPackage(height, blockRid, header, witness, ArrayList())
        }
    }

}