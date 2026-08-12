package net.postchain.gtv.merkle

import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.virtual.GtvVirtualFactory

public object GtvMerkleBasics {
    public const val HASH_PREFIX_NODE_GTV_ARRAY: Byte = 7
    public const val HASH_PREFIX_NODE_GTV_DICT: Byte = 8

    public const val UNKNOWN_COLLECTION_POSITION: Long = -10L

    private val proofFactory = GtvMerkleProofTreeFactory()

    public fun getGtvMerkleProofTreeFactory(): GtvMerkleProofTreeFactory = proofFactory

    public fun getGtvVirtualFactory(): GtvVirtualFactory = GtvVirtualFactory
}
