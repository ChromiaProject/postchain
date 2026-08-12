package net.postchain.gtv.merkle.proof

import net.postchain.common.data.Hash

/**
 * @property merkleHash is the calculated merkle hash
 * @property nrOfBytes is the size in bytes of the original structure we were hashing
 */
public data class MerkleHashSummary(val merkleHash: Hash, val nrOfBytes: Int) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is MerkleHashSummary && merkleHash.contentEquals(other.merkleHash))

    override fun hashCode(): Int = merkleHash.contentHashCode()
}
