// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

/**
 * Smallest message unit for ICMF
 */
data class IcmfMessage(
        val sender: BlockchainRid,
        val height: Long,  // Block height this message corresponds to
        val topic: String,
        val body: Gtv)

/**
 * Conceptually = all messages related to a block
 */
data class IcmfPacket(
        val height: Long, // Block height this package corresponds to
        val blockRid: ByteArray, // The BlockRid that goes with the header (for the cases where we cannot calculate it from the header)
        val rawHeader: ByteArray, // Header of the block
        val rawWitness: ByteArray, // Must send the witness so the recipient can validate
        val messages: List<IcmfMessage> // (potentially) messages
)

data class IcmfPackets<PtrT>(
    val currentPointer: PtrT,
    val packets: List<IcmfPacket>
)
