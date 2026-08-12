package net.postchain.gtv

import net.postchain.common.data.Hash
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.MerkleHashSummary
import net.postchain.gtv.merkle.proof.merkleHashSummary

/**
 * @param calculator describes the method we use for hashing and serialization
 * @return the merkle root hash (32 bytes) of the [Gtv] structure
 */
public fun Gtv.merkleHash(calculator: GtvMerkleHashCalculatorBase): Hash =
    merkleHashSummary(calculator).merkleHash

/**
 * @param calculator describes the method we use for hashing and serialization
 * @return the merkle root hash summary
 */
public fun Gtv.merkleHashSummary(calculator: GtvMerkleHashCalculatorBase): MerkleHashSummary = when (this) {
    // A virtual GTV has the proof element cached, and we cannot hash it directly since it has no hashCode().
    is GtvVirtual -> getGtvMerkleProofTree().merkleHashSummary(calculator)
    else -> calculator.merkleHashSummary(this)
}

/**
 * Creates a proof for the given top-level array indexes.
 */
public fun Gtv.generateProof(
    indexOfElementsToProve: List<Int>,
    calculator: GtvMerkleHashCalculatorBase,
): GtvMerkleProofTree {
    val gtvPathList: List<GtvPath> =
        indexOfElementsToProve.map { GtvPathFactory.buildFromArrayOfPointers(arrayOf(it)) }

    return this.generateProof(GtvPathSet(gtvPathList.toSet()), calculator)
}

/**
 * Creates a proof out of the given [GtvPathSet].
 */
public fun Gtv.generateProof(
    gtvPaths: GtvPathSet,
    calculator: GtvMerkleHashCalculatorBase,
): GtvMerkleProofTree = calculator.generateProof(this, gtvPaths)
