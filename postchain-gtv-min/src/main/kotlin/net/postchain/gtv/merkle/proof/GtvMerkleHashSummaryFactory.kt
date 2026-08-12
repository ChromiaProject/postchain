package net.postchain.gtv.merkle.proof

import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvBinaryTree
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.path.GtvPathSet

public class GtvMerkleHashSummaryFactory(
    treeFactory: GtvBinaryTreeFactory,
    proofFactory: GtvMerkleProofTreeFactory,
) : MerkleHashSummaryFactory<Gtv, GtvPathSet>(treeFactory, proofFactory) {

    override fun calculateMerkleRoot(
        value: Gtv,
        calculator: MerkleHashCalculator<Gtv, GtvPathSet>,
    ): MerkleHashSummary {
        val binaryTree = (treeFactory as GtvBinaryTreeFactory).buildFromGtv(value)
        val proofTree = (proofFactory as GtvMerkleProofTreeFactory).buildFromBinaryTree(binaryTree, calculator)
        return calculateMerkleRoot(proofTree, calculator)
    }

    override fun buildProofTree(
        value: Gtv,
        calculator: MerkleHashCalculator<Gtv, GtvPathSet>,
    ): GtvMerkleProofTree {
        val root: GtvBinaryTree = (treeFactory as GtvBinaryTreeFactory).buildFromGtv(value)
        return (proofFactory as GtvMerkleProofTreeFactory).buildFromBinaryTree(root, calculator)
    }
}
