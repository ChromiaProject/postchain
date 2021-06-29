package net.postchain.base.icmf

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
    val blockHeader: Gtv, // Header of the block
    val messages: List<IcmfMessage> // (potentially) messages
) {

    companion object {

        // Dummy used until we figure out how to do this right
        fun build(height: Long): IcmfPackage {
            val header: Gtv = GtvNull // TODO: Fix this
            return IcmfPackage(height, header, ArrayList())
        }
    }

}