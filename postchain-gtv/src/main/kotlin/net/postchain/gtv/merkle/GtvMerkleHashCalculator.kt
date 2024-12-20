// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Digester
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvCollection
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvPrimitive
import net.postchain.gtv.GtvString
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.proof.GtvMerkleHashSummaryFactory
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.MerkleHashSummary

/**
 * This should be the serialization we use in production
 *
 * @param gtv to serialize
 * @return the byte array containing serialized data
 */
fun serializeGtvToByteArray(gtv: Gtv): ByteArray {
    return when (gtv) {
        is GtvNull -> encodeGtv(gtv)
        is GtvInteger   -> encodeGtv(gtv)
        is GtvString    -> encodeGtv(gtv)
        is GtvByteArray -> encodeGtv(gtv)
        is GtvPrimitive -> {
            // TODO: Log a warning here? We don't know what this is!
            encodeGtv(gtv) // Hope for the best, because all primitives should be able to do this.
        }
        is GtvCollection -> throw ProgrammerMistake("Gtv is a collection (We should have transformed all collection-types to trees by now)")
        else             -> throw ProgrammerMistake("Note a primitive and not a collection: what is it? type: ${gtv.type}")
    }
}

abstract class GtvMerkleHashCalculatorBase(digester: Digester?) : MerkleHashCalculator<Gtv, GtvPathSet>(digester) {

    override fun generateProof(value: Gtv, pathSet: GtvPathSet): GtvMerkleProofTree {
        val factory = getHashSummaryFactory().treeFactory
        val proofFactory = GtvMerkleBasics.getGtvMerkleProofTreeFactory()

        val binaryTree = factory.buildBinaryTree(value, pathSet)

        return proofFactory.buildFromBinaryTree(binaryTree, this)
    }

    override fun merkleHashSummary(value: Gtv): MerkleHashSummary =
            getHashSummaryFactory().calculateMerkleRoot(value, this)

    override fun calculateNodeHash(prefix: Byte, hashLeft: Hash, hashRight: Hash): Hash {
        return calculateNodeHashInternal(prefix, hashLeft, hashRight, MerkleBasics::hashingFun)
    }

    /**
     * Leaf hashes are prefixed to tell them apart from internal nodes
     *
     * @param value The leaf
     * @return Returns the hash of the leaf.
     */
    override fun calculateLeafHash(value: Gtv): Hash {
        return calculateHashOfValueInternal(value, ::serializeGtvToByteArray, MerkleBasics::hashingFun)
    }

    override fun isContainerProofValueLeaf(value: Gtv): Boolean {
        return when (value) {
            is GtvCollection -> true
            is GtvPrimitive -> false
            else -> throw IllegalStateException("The type is neither collection or primitive. type: ${value.type} ")
        }
    }
}

/**
 * The first version of the calculator which contains a bug causing hash collisions.
 * Should only be used for backward compatibility
 */
class GtvMerkleHashCalculatorV1(digester: Digester) : GtvMerkleHashCalculatorBase(digester) {

    companion object {
        // These were singletons before so let's keep it that way I guess
        private val summaryFactory = GtvMerkleHashSummaryFactory(GtvBinaryTreeFactory(1), GtvMerkleProofTreeFactory())
    }

    constructor(cryptoSystem: CryptoSystem) : this(cryptoSystem as Digester)

    override fun getHashSummaryFactory() = summaryFactory
}

/**
 * The calculator intended to be used is production for trees that hold [Gtv]
 */
class GtvMerkleHashCalculatorV2(digester: Digester) : GtvMerkleHashCalculatorBase(digester) {

    companion object {
        // These were singletons before so let's keep it that way I guess
        private val summaryFactory = GtvMerkleHashSummaryFactory(GtvBinaryTreeFactory(2), GtvMerkleProofTreeFactory())
    }

    constructor(cryptoSystem: CryptoSystem) : this(cryptoSystem as Digester)

    override fun getHashSummaryFactory() = summaryFactory
}

fun makeMerkleHashCalculator(version: Long): GtvMerkleHashCalculatorBase = when (version) {
    1L -> GtvMerkleHashCalculatorV1(::sha256Digest)
    2L -> GtvMerkleHashCalculatorV2(::sha256Digest)
    else -> throw ProgrammerMistake("Unknown merkle hash version $version")
}
