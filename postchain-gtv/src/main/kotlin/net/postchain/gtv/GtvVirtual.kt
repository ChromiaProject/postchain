// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.MerkleProofElement


/**
 * A Virtual GTV pretends to be the original GTV structure, but really only holds very few values.
 *
 * If the user of the [GtvVirtual] tries to find a value that this structure does not have, an exception will be thrown.
 *
 * @property proofElement is cached here. It can be used to calculate the merkle root hash.
 */
abstract class GtvVirtual(private val proofElement: MerkleProofElement) : GtvCollection() {

    fun getGtvMerkleProofTree(): GtvMerkleProofTree = GtvMerkleProofTree(proofElement)

}




