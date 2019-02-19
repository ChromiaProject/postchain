package net.postchain.gtv.merkle.proof

import net.postchain.base.merkle.MerkleBasics.UNKNOWN_SIZE_IN_BYTE
import net.postchain.base.merkle.MerkleHashCalculator
import net.postchain.base.merkle.proof.*
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_ARRAY
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_DICT
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvMerkleBasics


const val SERIALIZATION_ARRAY_TYPE: Long = 103
const val SERIALIZATION_DICT_TYPE: Long = 104



/**
 * Represents a proof node that once was the head of a Gtv array
 *
 * Note: We keep the size in case we need to use a [GtvPath] to find a value
 */
class ProofNodeGtvArrayHead(val size: Int, left: MerkleProofElement, right: MerkleProofElement): ProofNode(HASH_PREFIX_NODE_GTV_ARRAY, left, right)

/**
 * Represents a proof node that once was the head of a Gtv dict.
 *
 * Note: In the case of dictionary, we don't strictly NEED to preserve the size, since we will use the key to find
 * our value, and a dictionary of size == 1 (only our key/value pair in it) will serve just as well, but we save the
 * size anyway for symmetry.
 */
class ProofNodeGtvDictHead(val size: Int, left: MerkleProofElement, right: MerkleProofElement): ProofNode(HASH_PREFIX_NODE_GTV_DICT, left, right)

/**
 * See [MerkleProofTree] for documentation
 *
 * @property root is the root of the proof
 * @property totalNrOfBytes is the size in bytes of the original [Gtv] structure (sometimes we don't have the size, e.g. after deserialization)
 */
class GtvMerkleProofTree(root: MerkleProofElement, totalNrOfBytes: Int = UNKNOWN_SIZE_IN_BYTE ): MerkleProofTree<Gtv>(root, totalNrOfBytes) {

    /**
     * Note that we have our own primitive serialization format. It is based on arrays that begins with an integer.
     * The integer tells us what the content is.
     * 0 -> just a hash
     * 1 -> value to be proven
     * 2 -> a node
     * (we can add more in sub classes)
     */
    fun serializeToGtv(): GtvArray {
        return serializeToGtvInternal(this.root)
    }


    /**
     * In this case the implementation is trivial. We already have a value of the
     * correct type, so let's return it.
     */
    fun serializeValueLeafToGtv(valueLeaf: Gtv): Gtv {
        return valueLeaf
    }


    fun serializeToGtvInternal(currentElement: MerkleProofElement): GtvArray {
        return when (currentElement) {
            is ProofHashedLeaf -> {
                val tail = GtvByteArray(currentElement.merkleHashCarrier.getHashWithPrefix()) // TODO: Q77: Should we really smack on prefix upon serialization?
                val head = GtvInteger(SERIALIZATION_HASH_LEAF_TYPE)
                val arr: Array<Gtv> = arrayOf(head, tail)
                GtvArray(arr)
            }
            is ProofValueLeaf<*> -> {
                val tail = serializeValueLeafToGtv(currentElement.content as Gtv)
                val head = GtvInteger(SERIALIZATION_VALUE_LEAF_TYPE)
                val arr: Array<Gtv> = arrayOf(head, tail)
                GtvArray(arr)
            }
            is ProofNodeSimple -> {
                val tail1 = serializeToGtvInternal(currentElement.left)
                val tail2 = serializeToGtvInternal(currentElement.right)
                val head = GtvInteger(SERIALIZATION_NODE_TYPE)
                val arr: Array<Gtv> = arrayOf(head, tail1, tail2)
                GtvArray(arr)
            }
            is ProofNodeGtvArrayHead -> {
                val tail1 = serializeToGtvInternal(currentElement.left)
                val tail2 = serializeToGtvInternal(currentElement.right)
                val size = GtvInteger(currentElement.size.toLong())
                val head = GtvInteger(SERIALIZATION_ARRAY_TYPE)
                val arr: Array<Gtv> = arrayOf(head, size, tail1, tail2)
                GtvArray(arr)
            }
            is ProofNodeGtvDictHead -> {
                val tail1 = serializeToGtvInternal(currentElement.left)
                val tail2 = serializeToGtvInternal(currentElement.right)
                val size = GtvInteger(currentElement.size.toLong())
                val head = GtvInteger(SERIALIZATION_DICT_TYPE)
                val arr: Array<Gtv> = arrayOf(head, size, tail1, tail2)
                GtvArray(arr)
            }
            else -> throw IllegalStateException("This type should have been taken care of: $currentElement")
        }
    }
}


/**
 * Calculates the merkle root hash of the proof structure.
 *
 * @param calculator describes the method we use for hashing and serialization
 * @return the merkle root hash summary
 */
fun GtvMerkleProofTree.merkleHashSummary(calculator: MerkleHashCalculator<Gtv>): MerkleHashSummary {

    val summaryFactory = GtvMerkleBasics.getGtvMerkleHashSummaryFactory()
    return summaryFactory.calculateMerkleRoot(this, calculator)
}
