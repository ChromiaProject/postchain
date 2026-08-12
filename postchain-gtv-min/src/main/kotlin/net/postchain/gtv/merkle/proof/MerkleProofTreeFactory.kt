package net.postchain.gtv.merkle.proof

import mu.KLogging
import net.postchain.gtv.merkle.BinaryTreeElement
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.Node

/**
 * Base class for building [MerkleProofTree]s from a binary tree or from the serialized format.
 */
public abstract class MerkleProofTreeFactory<T> {

    public companion object : KLogging()

    /**
     * Converts a [BinaryTreeElement] into a [MerkleProofElement]. The binary tree has already marked every
     * element that should be proven, so all that is left is turning the rest into hashes.
     */
    public abstract fun buildFromBinaryTreeInternal(
        currentElement: BinaryTreeElement,
        calculator: MerkleHashCalculator<T, *>,
    ): MerkleProofElement

    /**
     * Note: we cannot add to a cache, since a node does not map one-to-one to a source element.
     */
    protected fun convertNode(currentNode: Node, calculator: MerkleHashCalculator<T, *>): MerkleProofElement {
        val left = buildFromBinaryTreeInternal(currentNode.left, calculator)
        val right = buildFromBinaryTreeInternal(currentNode.right, calculator)
        return if (left is ProofHashedLeaf && right is ProofHashedLeaf) {
            // If both children are hashes, we must reduce them to a new (combined) hash.
            ProofHashedLeaf(calculator.calculateNodeHash(currentNode.getPrefixByte(), left.merkleHash, right.merkleHash))
        } else {
            buildNodeOfCorrectType(currentNode, left, right)
        }
    }

    /**
     * Override this in a subclass if there is more than one node type.
     */
    public open fun buildNodeOfCorrectType(
        currentNode: Node,
        left: MerkleProofElement,
        right: MerkleProofElement,
    ): ProofNode = ProofNode(currentNode.getPrefixByte(), left, right)
}
