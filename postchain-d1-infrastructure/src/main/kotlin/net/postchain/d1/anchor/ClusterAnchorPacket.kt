package net.postchain.d1.anchor

data class ClusterAnchorPacket(
        val height: Long, // Block height this package corresponds to
        val blockRid: ByteArray, // The BlockRid that goes with the header (for the cases where we cannot calculate it from the header)
        val rawHeader: ByteArray, // Header of the block
        val rawWitness: ByteArray, // Must send the witness so the recipient can validate
)
