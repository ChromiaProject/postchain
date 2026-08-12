package net.postchain.gtv.merkle.proof

import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvVirtual
import net.postchain.gtv.mapper.FromGtv
import net.postchain.gtv.mapper.ToGtv
import net.postchain.gtv.merkle.GtvMerkleBasics
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_ARRAY
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_DICT
import net.postchain.gtv.merkle.GtvMerkleBasics.UNKNOWN_COLLECTION_POSITION
import net.postchain.gtv.merkle.MerkleBasics.UNKNOWN_SIZE_IN_BYTE
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.path.SearchableGtvPathElement

public const val SERIALIZATION_ARRAY_TYPE: Long = 103
public const val SERIALIZATION_DICT_TYPE: Long = 104

/**
 * Like [ProofValueLeaf] but for [Gtv].
 *
 * @property pathElem tells us how to find this element in the surrounding collection
 */
public class ProofValueGtvLeaf(
    content: Gtv,
    sizeInBytes: Int,
    public val pathElem: SearchableGtvPathElement,
) : ProofValueLeaf<Gtv>(content, sizeInBytes)

/**
 * A proof node that once was the head of a Gtv array.
 *
 * @property size is the number of elements in the original array
 * @property pathElem is the position of the array in the collection above; null only at the proof root
 */
public class ProofNodeGtvArrayHead(
    public val size: Int,
    left: MerkleProofElement,
    right: MerkleProofElement,
    public val pathElem: SearchableGtvPathElement? = null,
) : ProofNode(HASH_PREFIX_NODE_GTV_ARRAY, left, right)

/**
 * A proof node that once was the head of a Gtv dict.
 *
 * @property size is the number of key-value pairs; kept for symmetry with the array case
 * @property pathElem is the position of the dict in the collection above; null only at the proof root
 */
public class ProofNodeGtvDictHead(
    public val size: Int,
    left: MerkleProofElement,
    right: MerkleProofElement,
    public val pathElem: SearchableGtvPathElement? = null,
) : ProofNode(HASH_PREFIX_NODE_GTV_DICT, left, right)

/**
 * @property totalNrOfBytes is the size in bytes of the original [Gtv] structure; unknown after deserialization
 */
public class GtvMerkleProofTree(
    root: MerkleProofElement,
    totalNrOfBytes: Int = UNKNOWN_SIZE_IN_BYTE,
) : MerkleProofTree<Gtv>(root, totalNrOfBytes), ToGtv {

    /**
     * A primitive serialization format based on arrays that begin with an integer telling us what the content is.
     */
    override fun toGtv(): GtvArray = serializeToGtvInternal(this.root)

    private fun serializeToGtvInternal(currentElement: MerkleProofElement): GtvArray = when (currentElement) {
        is ProofHashedLeaf -> GtvArray(
            arrayOf(GtvInteger(SERIALIZATION_HASH_LEAF_TYPE), GtvByteArray(currentElement.merkleHash))
        )

        is ProofValueGtvLeaf -> GtvArray(
            arrayOf(
                GtvInteger(SERIALIZATION_VALUE_LEAF_TYPE),
                serializePathElement(currentElement.pathElem),
                currentElement.content,
            )
        )

        is ProofNodeSimple -> GtvArray(
            arrayOf(
                GtvInteger(SERIALIZATION_NODE_TYPE),
                serializeToGtvInternal(currentElement.left),
                serializeToGtvInternal(currentElement.right),
            )
        )

        is ProofNodeGtvArrayHead -> GtvArray(
            arrayOf(
                GtvInteger(SERIALIZATION_ARRAY_TYPE),
                GtvInteger(currentElement.size.toLong()),
                serializePathElement(currentElement.pathElem),
                serializeToGtvInternal(currentElement.left),
                serializeToGtvInternal(currentElement.right),
            )
        )

        is ProofNodeGtvDictHead -> GtvArray(
            arrayOf(
                GtvInteger(SERIALIZATION_DICT_TYPE),
                GtvInteger(currentElement.size.toLong()),
                serializePathElement(currentElement.pathElem),
                serializeToGtvInternal(currentElement.left),
                serializeToGtvInternal(currentElement.right),
            )
        )

        else -> throw IllegalStateException("This type should have been taken care of: $currentElement")
    }

    private fun serializePathElement(pathElem: SearchableGtvPathElement?): Gtv =
        pathElem?.buildGtv() ?: GtvInteger(UNKNOWN_COLLECTION_POSITION)

    public companion object : FromGtv<GtvMerkleProofTree> {
        override fun fromGtv(gtv: Gtv): GtvMerkleProofTree =
            GtvMerkleProofTreeFactory().deserialize(gtv as GtvArray)
    }
}

/**
 * Calculates the merkle root hash of the proof structure.
 */
public fun GtvMerkleProofTree.merkleHash(calculator: MerkleHashCalculator<Gtv, GtvPathSet>): Hash =
    this.merkleHashSummary(calculator).merkleHash

/**
 * Calculates the merkle root hash summary of the proof structure.
 */
public fun GtvMerkleProofTree.merkleHashSummary(
    calculator: MerkleHashCalculator<Gtv, GtvPathSet>,
): MerkleHashSummary = calculator.getHashSummaryFactory().calculateMerkleRoot(this, calculator)

/**
 * @return a virtual GTV version of the original [Gtv], built only from what the proof retained, so every
 *         hashed-away value reads as absent
 */
public fun GtvMerkleProofTree.toGtvVirtual(): GtvVirtual =
    GtvMerkleBasics.getGtvVirtualFactory().buildGtvVirtual(this)
