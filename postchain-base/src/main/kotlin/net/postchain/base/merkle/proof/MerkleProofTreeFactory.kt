package net.postchain.base.merkle.proof

import net.postchain.base.merkle.*

/**
 * Base class for building [MerkleProofTree] (but needs to be overridden to actually do something)
 *
 * Can build from:
 * 1. A binary tree (this means you first have to build a binary tree before you can build the proof,
 *     see [BinaryTreeFactory] )
 * 2. The serialized format
 */
abstract class MerkleProofTreeFactory<T>() {

    /**
     * Converts [BinaryTreeElement] into a [MerkleProofElement].
     *
     * Note: that the [BinaryTree] already has marked all elements that should be proven, so all we have to
     * do now is to convert the rest to hashes.
     *
     * @param currentElement is the element we will use as root of the tree
     * @param calculator is the class we use for hash calculation
     * @return the [MerkleProofElement] we have built.
     */
    abstract fun buildFromBinaryTreeSub(currentElement: BinaryTreeElement, calculator: MerkleHashCalculator<T>):
            MerkleProofElement


    /**
     * Note: we cannot add to the cache, since a node does not map one-to-one to a source element.
     */
    protected fun convertNode(currentNode: Node, calculator: MerkleHashCalculator<T>): MerkleProofElement {
        val left = buildFromBinaryTreeSub(currentNode.left, calculator)
        val right = buildFromBinaryTreeSub(currentNode.right, calculator)
        return if (left is ProofHashedLeaf && right is ProofHashedLeaf) {
            // If both children are hashes, then
            // we must reduce them to a new (combined) hash.
            val addedHash = calculator.calculateNodeHash(
                    currentNode.getPrefixByte(),
                    left.merkleHashCarrier,
                    right.merkleHashCarrier)
            ProofHashedLeaf(addedHash)
        } else {
            buildNodeOfCorrectType(currentNode, left, right)
        }
    }

    /**
     * Override this in subclass if there is more than one node type.
     *
     * @param currentNode is the node we should convert to [ProofNode]
     * @return the [ProofNode] implementation that should be used for this [Node]
     */
    open fun buildNodeOfCorrectType(currentNode: Node, left: MerkleProofElement, right: MerkleProofElement): ProofNode {
        return ProofNode(currentNode.getPrefixByte(), left, right)
    }


}