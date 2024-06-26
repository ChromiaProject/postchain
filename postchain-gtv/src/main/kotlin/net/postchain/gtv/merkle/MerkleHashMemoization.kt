// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.gtv.merkle.proof.MerkleHashSummary

/**
 * Abstract base class for memoization of hashed values
 */
abstract class MerkleHashMemoization<T> {

    // See sub class
    abstract fun findMerkleHash(src: T): MerkleHashSummary?

    // See sub class
    abstract fun add(src: T, newSummary: MerkleHashSummary)
}