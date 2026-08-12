package net.postchain.gtv.merkle.proof

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.merkle.BinaryTree
import net.postchain.gtv.merkle.BinaryTreeElement
import net.postchain.gtv.merkle.EmptyLeaf
import net.postchain.gtv.merkle.GtvArrayHeadNode
import net.postchain.gtv.merkle.GtvDictHeadNode
import net.postchain.gtv.merkle.GtvMerkleBasics
import net.postchain.gtv.merkle.Leaf
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.Node
import net.postchain.gtv.merkle.SubTreeRootNode
import net.postchain.gtv.merkle.path.ArrayGtvPathElement
import net.postchain.gtv.merkle.path.DictGtvPathElement
import net.postchain.gtv.merkle.path.GtvPathElement
import net.postchain.gtv.merkle.path.GtvPathLeafElement
import net.postchain.gtv.merkle.path.SearchableGtvPathElement

/**
 * Builds [GtvMerkleProofTree]s from a binary tree or from the serialized format.
 */
public class GtvMerkleProofTreeFactory : MerkleProofTreeFactory<Gtv>() {

    /**
     * The [net.postchain.gtv.merkle.GtvBinaryTree] has already marked all elements that should be proven, so all
     * we have to do now is convert the rest to hashes.
     */
    public fun buildFromBinaryTree(
        originalTree: BinaryTree<Gtv>,
        calculator: MerkleHashCalculator<Gtv, *>,
    ): GtvMerkleProofTree =
        GtvMerkleProofTree(buildFromBinaryTreeInternal(originalTree.root, calculator), originalTree.root.getNrOfBytes())

    override fun buildFromBinaryTreeInternal(
        currentElement: BinaryTreeElement,
        calculator: MerkleHashCalculator<Gtv, *>,
    ): MerkleProofElement = when (currentElement) {
        is EmptyLeaf -> ProofHashedLeaf(EMPTY_HASH) // Just zeros

        is Leaf<*> -> {
            val content: Gtv = currentElement.content as Gtv
            when (val pathElem = currentElement.getPathElement()) {
                null -> {
                    val hash: Hash = calculator.calculateLeafHash(content)
                    ProofHashedLeaf(hash)
                }

                is GtvPathLeafElement -> ProofValueGtvLeaf(content, currentElement.sizeInBytes, pathElem.previous!!)

                else -> throw IllegalStateException(
                    "The path and structure don't match. We are at a leaf, but path elem is not a leaf: $pathElem "
                )
            }
        }

        is SubTreeRootNode<*> -> {
            val content: Gtv = currentElement.content as Gtv
            val pathElem = currentElement.getPathElement()
            if (pathElem is GtvPathLeafElement) {
                ProofValueGtvLeaf(content, currentElement.getNrOfBytes(), pathElem.previous!!)
            } else {
                convertNode(currentElement, calculator)
            }
        }

        is Node -> convertNode(currentElement, calculator)

        else -> throw IllegalStateException("Cannot handle $currentElement")
    }

    override fun buildNodeOfCorrectType(
        currentNode: Node,
        left: MerkleProofElement,
        right: MerkleProofElement,
    ): ProofNode = when (currentNode) {
        is GtvArrayHeadNode ->
            ProofNodeGtvArrayHead(currentNode.size, left, right, extractSearchablePathElement(currentNode))

        is GtvDictHeadNode ->
            ProofNodeGtvDictHead(currentNode.size, left, right, extractSearchablePathElement(currentNode))

        else -> ProofNodeSimple(left, right)
    }

    private fun extractSearchablePathElement(currentNode: SubTreeRootNode<Gtv>): SearchableGtvPathElement? =
        (currentNode.getPathElement() as GtvPathElement?)?.previous

    // ---------- Deserialization -----

    /**
     * @param serializedRootArrayGtv is the root element of the serialized structure
     * @return the proof tree as it looked before serialization
     */
    public fun deserialize(serializedRootArrayGtv: GtvArray): GtvMerkleProofTree =
        // The size is unknown, since we don't have the original GTV struct at hand.
        GtvMerkleProofTree(deserializeSub(serializedRootArrayGtv))

    private fun deserializeSub(currentSerializedArrayGtv: GtvArray): MerkleProofElement {
        val typeCode = (currentSerializedArrayGtv[0] as GtvInteger).asInteger()
        val secondElement = currentSerializedArrayGtv[1]
        return when (typeCode) {
            SERIALIZATION_HASH_LEAF_TYPE -> ProofHashedLeaf((secondElement as GtvByteArray).bytearray)

            SERIALIZATION_VALUE_LEAF_TYPE -> {
                // If the path element is null, the proof structure is incorrect.
                val pathElem = deserializePathElement(secondElement)!!
                val gtvContent = currentSerializedArrayGtv[2]
                ProofValueGtvLeaf(gtvContent, gtvContent.nrOfBytes(), pathElem)
            }

            SERIALIZATION_NODE_TYPE -> ProofNodeSimple(
                deserializeSub(secondElement as GtvArray),
                deserializeSub(currentSerializedArrayGtv[2] as GtvArray),
            )

            SERIALIZATION_ARRAY_TYPE -> ProofNodeGtvArrayHead(
                (secondElement as GtvInteger).integer.toInt(),
                deserializeSub(currentSerializedArrayGtv[3] as GtvArray),
                deserializeSub(currentSerializedArrayGtv[4] as GtvArray),
                deserializePathElement(currentSerializedArrayGtv[2]),
            )

            SERIALIZATION_DICT_TYPE -> ProofNodeGtvDictHead(
                (secondElement as GtvInteger).integer.toInt(),
                deserializeSub(currentSerializedArrayGtv[3] as GtvArray),
                deserializeSub(currentSerializedArrayGtv[4] as GtvArray),
                deserializePathElement(currentSerializedArrayGtv[2]),
            )

            else -> throw IllegalStateException("Should handle the type $typeCode")
        }
    }

    /**
     * @return a path element, or null for the root Gtv collection
     */
    private fun deserializePathElement(src: Gtv): SearchableGtvPathElement? = when (src) {
        is GtvString -> DictGtvPathElement(null, src.string)

        is GtvInteger -> if (src.integer != GtvMerkleBasics.UNKNOWN_COLLECTION_POSITION) {
            ArrayGtvPathElement(null, src.integer.toInt())
        } else {
            null
        }

        else -> throw IllegalArgumentException(
            "The GTV at this position must be either GtvInteger or GtvString, but is: $src"
        )
    }
}
