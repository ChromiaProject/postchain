package net.postchain.gtv.merkle.proof

import net.postchain.common.data.Hash
import net.postchain.gtv.merkle.BinaryTreeFactory
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.path.PathSet

/**
 * Takes you directly from a source structure to the merkle root hash. Does not handle proofs.
 *
 * Calculating the merkle root of a proof of a collection means the value-to-be-proved must itself be
 * transformed into a binary tree first, which is why this lives in a factory rather than in [MerkleProofTree].
 */
public abstract class MerkleHashSummaryFactory<T, TPathSet : PathSet>(
    public val treeFactory: BinaryTreeFactory<T, TPathSet>,
    public val proofFactory: MerkleProofTreeFactory<T>,
) {

    /**
     * Calculates all the way from source type to merkle hash.
     */
    public abstract fun calculateMerkleRoot(value: T, calculator: MerkleHashCalculator<T, TPathSet>): MerkleHashSummary

    /**
     * Calculates the merkle root of the given proof tree. If the original structure was a block, a valid proof
     * yields the merkle root of that block.
     */
    public fun calculateMerkleRoot(
        proofTree: MerkleProofTree<T>,
        calculator: MerkleHashCalculator<T, TPathSet>,
    ): MerkleHashSummary =
        MerkleHashSummary(calculateMerkleRootInternal(proofTree.root, calculator), proofTree.totalNrOfBytes)

    @Suppress("UNCHECKED_CAST")
    private fun calculateMerkleRootInternal(
        currentElement: MerkleProofElement,
        calculator: MerkleHashCalculator<T, TPathSet>,
    ): Hash = when (currentElement) {
        is ProofHashedLeaf -> currentElement.merkleHash

        is ProofValueLeaf<*> -> {
            val value = currentElement.content as T
            if (calculator.isContainerProofValueLeaf(value)) {
                // A container value has to become a binary tree before it can be hashed
                calculateMerkleRootInternal(buildProofTree(value, calculator).root, calculator)
            } else {
                calculator.calculateLeafHash(value)
            }
        }

        is ProofNode -> calculator.calculateNodeHash(
            currentElement.prefix,
            calculateMerkleRootInternal(currentElement.left, calculator),
            calculateMerkleRootInternal(currentElement.right, calculator),
        )

        else -> throw IllegalStateException("Should have handled this type: $currentElement")
    }

    public abstract fun buildProofTree(value: T, calculator: MerkleHashCalculator<T, TPathSet>): MerkleProofTree<T>
}
