// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.proof

import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvBinaryTree
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.path.GtvPathSet

class GtvMerkleHashSummaryFactory(
        treeFactory: GtvBinaryTreeFactory,
        proofFactory: GtvMerkleProofTreeFactory
): MerkleHashSummaryFactory<Gtv, GtvPathSet>(treeFactory, proofFactory) {

    /**
     * Note: should have looked in cache before this, because here we will do the calculation no matter what.
     */
    override fun calculateMerkleRoot(value: Gtv, calculator: MerkleHashCalculator<Gtv>): MerkleHashSummary {
        val gtvTreeFactory = treeFactory as GtvBinaryTreeFactory
        val binaryTree = gtvTreeFactory.buildFromGtv(value)

        val gtvProofFactory = proofFactory as GtvMerkleProofTreeFactory
        val proofTree = gtvProofFactory.buildFromBinaryTree(binaryTree, calculator)

        return calculateMerkleRoot(proofTree, calculator)
    }

    override fun buildProofTree(value: Gtv, calculator: MerkleHashCalculator<Gtv>): GtvMerkleProofTree {
        val gtvTreeFactory = treeFactory as GtvBinaryTreeFactory
        val root: GtvBinaryTree = gtvTreeFactory.buildFromGtv(value)
        val gtvProofFactory = proofFactory as GtvMerkleProofTreeFactory
        return gtvProofFactory.buildFromBinaryTree(root, calculator)
    }
}