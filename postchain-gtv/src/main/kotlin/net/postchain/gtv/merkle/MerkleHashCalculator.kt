// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.common.data.Hash
import net.postchain.crypto.Digester
import net.postchain.gtv.merkle.path.PathSet
import net.postchain.gtv.merkle.proof.MerkleHashSummary
import net.postchain.gtv.merkle.proof.MerkleHashSummaryFactory
import net.postchain.gtv.merkle.proof.MerkleProofTree

/**
 * Abstract class responsible for calculating hashes and serialization.
 * Can calculate hashes of leaves and nodes.
 *
 * Note: We make this class abstract so we can use a dummy version during test (this makes tests easier to understand).
 */
abstract class MerkleHashCalculator<T, TPathSet : PathSet>(digester: Digester?) : BinaryNodeHashCalculator(digester) {

    abstract fun getHashSummaryFactory(): MerkleHashSummaryFactory<T, TPathSet>

    abstract fun generateProof(value: T, pathSet: TPathSet): MerkleProofTree<T>

    abstract fun merkleHashSummary(value: T): MerkleHashSummary

    /**
     * Leaf hashes are prefixed to tell them apart from internal nodes.
     *
     * @param value The leaf
     * @return the hash of a leaf.
     */
    abstract fun calculateLeafHash(value: T): Hash


    /**
     * We will smack on the "leaf prefix" to the binary data before the hashing function is run.
     *
     * @param valueToHash The leaf
     * @param serializeFun The only reason we pass the function as a parameter is to simplify testing.
     * @param hashFun The only reason we pass the function as a parameter is to simplify testing.
     * @return the hash of the valueToHash.
     */
    protected fun calculateHashOfValueInternal(
            valueToHash: T,
            serializeFun: (T) -> ByteArray,
            hashFun: (ByteArray, Digester?) -> Hash
    ): Hash {
        val byteArr: ByteArray = byteArrayOf(MerkleBasics.HASH_PREFIX_LEAF) + serializeFun(valueToHash)
        return hashFun(byteArr, digester)
    }

    /**
     * Note: We must override this method if the value can be a container (as in the case with GTV).
     *
     * @return True if the value can hold other values (i.e. if it is a container of sorts).
     */
    open fun isContainerProofValueLeaf(value: T): Boolean = false

}