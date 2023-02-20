// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.merkle.proof.SERIALIZATION_ARRAY_TYPE
import net.postchain.gtv.merkle.proof.SERIALIZATION_DICT_TYPE
import net.postchain.gtv.merkle.proof.SERIALIZATION_HASH_LEAF_TYPE
import net.postchain.gtv.merkle.proof.SERIALIZATION_NODE_TYPE
import net.postchain.gtv.merkle.proof.SERIALIZATION_VALUE_LEAF_TYPE

object GtvProofTreeTestHelper {

    /**
     * Will traverse a GTV block proof tree recursively until it finds the TX hash to be proven (given as param)
     *
     * @param hashToProve is the value to be proven (that should be somewhere in this proof
     * @return true if [hashToProve] is found
     */
    fun findHashInBlockProof(hashToProve: ByteArray, list: GtvArray): Boolean {
        val code = list[0].asInteger()
        when (code) {
            SERIALIZATION_VALUE_LEAF_TYPE -> { // 101
                // Found it!
                return true
            }

            SERIALIZATION_HASH_LEAF_TYPE -> { // 100
                // Nothing interesting here, move on
                return false
            }

            SERIALIZATION_ARRAY_TYPE -> {  // 103
                val leftSide = list[3] as GtvArray
                if (findHashInBlockProof(hashToProve, leftSide)) {
                    return true
                } else {
                    val rightSide = list[4] as GtvArray
                    if (findHashInBlockProof(hashToProve, rightSide)) {
                        return true
                    }
                }
            }

            SERIALIZATION_NODE_TYPE -> { // 102 (intermediary node, not sure why it's used in proof)
                val leftSide = list[1] as GtvArray
                if (findHashInBlockProof(hashToProve, leftSide)) {
                    return true
                } else {
                    val rightSide = list[2] as GtvArray
                    if (findHashInBlockProof(hashToProve, rightSide)) {
                        return true
                    }
                }
            }

            SERIALIZATION_DICT_TYPE -> throw IllegalStateException("Dict not expected in a block proof")
            else -> throw IllegalStateException("Don't know this code: $code")
        }
        throw IllegalStateException("How the hell did we get here?")
    }

}
