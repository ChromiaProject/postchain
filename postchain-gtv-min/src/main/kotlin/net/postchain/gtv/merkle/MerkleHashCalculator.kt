package net.postchain.gtv.merkle

import net.postchain.common.data.Hash
import net.postchain.crypto.Digester
import net.postchain.gtv.merkle.path.PathSet
import net.postchain.gtv.merkle.proof.MerkleHashSummary
import net.postchain.gtv.merkle.proof.MerkleHashSummaryFactory
import net.postchain.gtv.merkle.proof.MerkleProofTree

/**
 * Calculates hashes of leaves and nodes, and serializes leaves.
 *
 * Abstract so tests can substitute a dummy version.
 */
public abstract class MerkleHashCalculator<T, TPathSet : PathSet>(
    digester: Digester?,
) : BinaryNodeHashCalculator(digester) {

    public abstract fun getHashSummaryFactory(): MerkleHashSummaryFactory<T, TPathSet>

    public abstract fun generateProof(value: T, pathSet: TPathSet): MerkleProofTree<T>

    public abstract fun merkleHashSummary(value: T): MerkleHashSummary

    /**
     * Leaf hashes are prefixed to tell them apart from internal nodes.
     */
    public abstract fun calculateLeafHash(value: T): Hash

    /**
     * The serialize and hash functions are parameters only to simplify testing.
     */
    protected fun calculateHashOfValueInternal(
        valueToHash: T,
        serializeFun: (T) -> ByteArray,
        hashFun: (ByteArray, Digester?) -> Hash,
    ): Hash = hashFun(byteArrayOf(MerkleBasics.HASH_PREFIX_LEAF) + serializeFun(valueToHash), digester)

    /**
     * Must be overridden if the value can be a container (as in the case with GTV).
     */
    public open fun isContainerProofValueLeaf(value: T): Boolean = false
}
