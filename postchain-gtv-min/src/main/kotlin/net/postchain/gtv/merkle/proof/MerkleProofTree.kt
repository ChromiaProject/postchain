package net.postchain.gtv.merkle.proof

import net.postchain.common.data.Hash
import net.postchain.gtv.merkle.MerkleBasics.HASH_PREFIX_NODE
import net.postchain.gtv.merkle.MerkleBasics.UNKNOWN_SIZE_IN_BYTE

public const val SERIALIZATION_HASH_LEAF_TYPE: Long = 100
public const val SERIALIZATION_VALUE_LEAF_TYPE: Long = 101
public const val SERIALIZATION_NODE_TYPE: Long = 102

/**
 * A clone of the original merkle tree where most values are replaced by hashes, proving that the values that
 * were *not* replaced belong to the tree.
 *
 * ```
 *          root                     x == hash( hash(hash1 + hash(V1)) + hash2 )
 *      node1     hash2
 *   hash1  V1
 * ```
 *
 * A proof may hold several values, in which case each retained value contributes its own hash to the chain.
 */
public interface MerkleProofElement

/**
 * Base class for nodes.
 *
 * @property prefix is the prefix to add during merkle root hash calculation
 */
public open class ProofNode(
    public val prefix: Byte,
    public val left: MerkleProofElement,
    public val right: MerkleProofElement,
) : MerkleProofElement

/**
 * A dummy node that does not represent anything in the original structure.
 */
public class ProofNodeSimple(
    left: MerkleProofElement,
    right: MerkleProofElement,
) : ProofNode(HASH_PREFIX_NODE, left, right)

/**
 * The data we want to prove exists in the merkle tree.
 *
 * @property sizeInBytes is the nr of bytes the original object takes up
 */
public open class ProofValueLeaf<T>(
    public val content: T,
    public val sizeInBytes: Int,
) : MerkleProofElement

/**
 * @property merkleHash is the hash of a sub tree that isn't interesting for this proof
 */
public data class ProofHashedLeaf(val merkleHash: Hash) : MerkleProofElement {

    override fun equals(other: Any?): Boolean =
        this === other || (other is ProofHashedLeaf && merkleHash.contentEquals(other.merkleHash))

    override fun hashCode(): Int = merkleHash.contentHashCode()
}

/**
 * The "proof tree" can be used to prove that one or more values is/are indeed part of the merkle tree.
 */
public abstract class MerkleProofTree<T>(
    public val root: MerkleProofElement,
    public val totalNrOfBytes: Int = UNKNOWN_SIZE_IN_BYTE,
) {

    /** Mostly for debugging */
    public fun maxLevel(): Int = maxLevelInternal(root)

    private fun maxLevelInternal(node: MerkleProofElement): Int = when (node) {
        is ProofValueLeaf<*> -> 1
        is ProofHashedLeaf -> 1
        is ProofNode -> maxOf(maxLevelInternal(node.left), maxLevelInternal(node.right)) + 1
        else -> throw IllegalStateException("Should be able to handle node type: $node")
    }
}
