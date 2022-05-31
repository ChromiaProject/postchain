package net.postchain.eif.merkle

import net.postchain.common.data.Hash

object MerkleTestUtil {
    fun getMerkleProof(proofs: List<Hash>, pos: Int, leaf: Hash, hashFunction: (Hash, Hash) -> Hash): Hash {
        var r = leaf
        proofs.forEachIndexed { i, h ->
            r = if (((pos shr i) and 1) != 0) {
                hashFunction(h, r)
            } else {
                hashFunction(r, h)
            }
        }
        return r
    }
}