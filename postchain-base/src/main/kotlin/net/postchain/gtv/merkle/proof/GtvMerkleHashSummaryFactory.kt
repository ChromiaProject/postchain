// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.proof

import net.postchain.base.merkle.MerkleHashCalculator
import net.postchain.base.merkle.proof.MerkleHashSummary
import net.postchain.base.merkle.proof.MerkleHashSummaryFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvBinaryTree
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.path.GtvPathSet

class GtvMerkleHashSummaryFactory(
        treeFactory: GtvBinaryTreeFactory,
        proofFactory: GtvMerkleProofTreeFactory
): MerkleHashSummaryFactory<Gtv, GtvPathSet>(treeFactory, proofFactory) {

    /**
     * Note: should have looked in cache before this, because here we will do the calculation no matter what.
     */
    override fun calculateMerkleRoot(value: Gtv, calculator: MerkleHashCalculator<Gtv>, includePrefix: Boolean): MerkleHashSummary {
        val gtvTreeFactory = treeFactory as GtvBinaryTreeFactory
        val binaryTree = gtvTreeFactory.buildFromGtv(value)

        val gtvProofFactory = proofFactory as GtvMerkleProofTreeFactory
        val proofTree = gtvProofFactory.buildFromBinaryTree(binaryTree, calculator, includePrefix)

        return calculateMerkleRoot(proofTree, calculator, includePrefix)
    }

    override fun buildProofTree(value: Gtv, calculator: MerkleHashCalculator<Gtv>, includePrefix: Boolean): GtvMerkleProofTree {
        val gtvTreeFactory = treeFactory as GtvBinaryTreeFactory
        val root: GtvBinaryTree = gtvTreeFactory.buildFromGtv(value)
        val gtvProofFactory = proofFactory as GtvMerkleProofTreeFactory
        return gtvProofFactory.buildFromBinaryTree(root, calculator, includePrefix)
    }
}