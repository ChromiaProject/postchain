// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

/**
 * Conceptually = all messages related to a block
 */
data class IcmfPacket(
        val height: Long, // Block height this package corresponds to
        val sender: BlockchainRid,
        val topic: String,
        val blockRid: ByteArray, // The BlockRid that goes with the header (for the cases where we cannot calculate it from the header)
        val rawHeader: ByteArray, // Header of the block
        val rawWitness: ByteArray, // Must send the witness so the recipient can validate
        val prevMessageBlockHeight: Long,
        val bodies: List<Gtv> // (potentially) messages
)

data class IcmfPackets<PtrT>(
        val currentPointer: PtrT,
        val packets: List<IcmfPacket>,
        val sizeBytes: Int
)
