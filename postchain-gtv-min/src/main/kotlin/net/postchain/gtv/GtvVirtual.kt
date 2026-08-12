package net.postchain.gtv

import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.MerkleProofElement

/**
 * A virtual GTV pretends to be the original GTV structure, but really only holds very few values.
 *
 * Asking it for a value it does not have throws, rather than returning null, because a missing value means
 * "hashed away by the proof", not "absent from the original".
 *
 * @property proofElement is cached here. It can be used to calculate the merkle root hash.
 */
public abstract class GtvVirtual(private val proofElement: MerkleProofElement) : GtvCollection() {
    public fun getGtvMerkleProofTree(): GtvMerkleProofTree = GtvMerkleProofTree(proofElement)
}
