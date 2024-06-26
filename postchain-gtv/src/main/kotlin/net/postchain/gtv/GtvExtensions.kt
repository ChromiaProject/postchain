package net.postchain.gtv

import net.postchain.common.data.Hash
import net.postchain.gtv.merkle.GtvMerkleBasics
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.MerkleHashSummary
import net.postchain.gtv.merkle.proof.merkleHashSummary


/**
 * Calculates the merkle root hash of the structure.
 *
 * Note: if the hash is present in the cache, the cached value will be returned instead.
 *
 * @param calculator describes the method we use for hashing and serialization
 * @return the merkle root hash (32 bytes) of the [Gtv] structure.
 */
fun Gtv.merkleHash(calculator: MerkleHashCalculator<Gtv>): Hash {
    return merkleHashSummary(calculator).merkleHash
}

/**
 * Calculates the merkle root hash of the structure OR fetches it from the cache.
 *
 * 1. We will begin by looking in the [Gtv] 's local cache
 * 2. If not found, we look in the global cache
 * 3. If not found, we need to calculate it
 * 4. Remember to update both caches if they were empty
 *
 * @param calculator describes the method we use for hashing and serialization
 * @return the merkle root hash summary
 */
fun Gtv.merkleHashSummary(calculator: MerkleHashCalculator<Gtv>): MerkleHashSummary {
    return when (this) {
        is GtvVirtual -> {
            // We have cached the proof element for this object inside the GTV Virtual
            // and we cannot use the cache directly on the virtual GTV since it doesn't even have a hashCode() impl.
            val proofTree: GtvMerkleProofTree = this.getGtvMerkleProofTree()
            proofTree.merkleHashSummary(calculator)
        }
        else -> {
            val summaryFactory = GtvMerkleBasics.getGtvMerkleHashSummaryFactory()
            summaryFactory.calculateMerkleRoot(this, calculator)
        }
    }
}

/**
 * Creates a proof out of the given path described as indexs of an array
 *
 * @param array indexes that points to the element we need to prove
 * @param calculator describes the method we use for hashing and serialization
 * @return the created proof tree
 */
fun Gtv.generateProof(indexOfElementsToProve: List<Int>, calculator: MerkleHashCalculator<Gtv>): GtvMerkleProofTree {

    val gtvPathList: List<GtvPath> = indexOfElementsToProve.map { GtvPathFactory.buildFromArrayOfPointers(arrayOf(it)) }
    val gtvPaths = GtvPathSet(gtvPathList.toSet())
    return this.generateProof(gtvPaths, calculator)
}

/**
 * Creates a proof out of the given [GtvPathSet]
 *
 * @param gtvPaths the paths to the elements we need to prove
 * @param calculator describes the method we use for hashing and serialization
 * @return the created proof tree
 */
fun Gtv.generateProof(gtvPaths: GtvPathSet, calculator: MerkleHashCalculator<Gtv>): GtvMerkleProofTree {
    val factory = GtvMerkleBasics.getGtvBinaryTreeFactory()
    val proofFactory = GtvMerkleBasics.getGtvMerkleProofTreeFactory()

    val binaryTree = factory.buildFromGtvAndPath(this ,gtvPaths)

    return proofFactory.buildFromBinaryTree(binaryTree, calculator)
}
