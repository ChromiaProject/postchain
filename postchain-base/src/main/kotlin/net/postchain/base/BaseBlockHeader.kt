// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.InitialBlockData
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkleHash

/**
 * BaseBlockHeader implements elements and functionality that are necessary to describe and operate on a block header
 *
 * @property rawData DER encoded data including the previous blocks RID ([prevBlockRID]) and [timestamp]
 * @property cryptoSystem An implementation of the various cryptographic primitives to use
 * @property timestamp  Specifies the time that a block was created as the number
 *                      of milliseconds since midnight January 1st 1970 UTC
 */
class BaseBlockHeader(override val rawData: ByteArray, private val merkleHashCalculator: GtvMerkleHashCalculator) : BlockHeader {
    override val prevBlockRID: ByteArray
    override val blockRID: ByteArray
    val blockHeightDependencyArray: Array<Hash?>
    val extraData: Map<String, Gtv>
    val timestamp: Long get() = blockHeaderRec.getTimestamp()
    val blockHeaderRec: BlockHeaderData = BlockHeaderData.fromBinary(rawData)

    init {
        prevBlockRID = blockHeaderRec.getPreviousBlockRid()
        blockRID = blockHeaderRec.toGtv().merkleHash(merkleHashCalculator)
        blockHeightDependencyArray = blockHeaderRec.getBlockHeightDependencyArray()
        extraData = blockHeaderRec.getExtra()
    }

    /**
     * @param depsRequired number of dependencies needed in the block header
     * @return true if there are the same number of elements in the block header as in the configuration
     *          (it's lame, but it's the best we can do, since we allow "null")
     */
    fun checkCorrectNumberOfDependencies(depsRequired: Int): Boolean {
        return depsRequired == blockHeightDependencyArray.size
    }

    fun checkExtraData(expectedExtraData: Map<String, Gtv>): Boolean {
        return extraData == expectedExtraData
    }

    companion object Factory {
        /**
         * Utility to simplify creating an instance of BaseBlockHeader
         *
         * @param cryptoSystem Cryptographic utilities
         * @param iBlockData Initial block data including previous block identifier, timestamp and height
         * @param rootHash Merkle tree root hash
         * @param timestamp timestamp
         * @param extraData
         * @return Serialized block header
         */
        @JvmStatic
        fun make(
            merkleHashCalculator: GtvMerkleHashCalculator,
            iBlockData: InitialBlockData,
            rootHash: ByteArray,
            timestamp: Long,
            extraData: Map<String, Gtv>
        ): BaseBlockHeader {
            val gtvBhd = BlockHeaderData.fromDomainObjects(iBlockData, rootHash, timestamp, extraData)

            val raw = GtvEncoder.encodeGtv(gtvBhd.toGtv())
            return BaseBlockHeader(raw, merkleHashCalculator)
        }
    }

    /**
     * Return a Merkle proof tree of a hash in a Merkle tree
     *
     * @param txHash Target hash for which the Merkle path is wanted
     * @param txHashes All hashes are the leaves part of this Merkle tree
     * @return The Merkle proof tree for [txHash]
     */
    fun merkleProofTree(txHash: WrappedByteArray, txHashes: Array<WrappedByteArray>): Pair<Long, GtvMerkleProofTree> {
        //println("looking for tx hash: ${txHash.toHex()} in array where first is: ${txHashes[0].toHex()}")
        val positionOfOurTxToProve = txHashes.indexOf(txHash) //txHash.positionInArray(txHashes)
        if (positionOfOurTxToProve < 0) {
            throw UserMistake("We cannot prove this transaction (hash: ${txHash.toHex()}), because it is not in the block")
        }
        val gtvArray = gtv(txHashes.map { gtv(it.data) })
        return Pair(positionOfOurTxToProve.toLong(), gtvArray.generateProof(listOf(positionOfOurTxToProve), merkleHashCalculator))
    }

    /**
     * Validate that a Merkle path connects the target hash to the root hash in the block header
     *
     * @param merklePath The Merkle path
     * @param targetTxHash Target hash to validate path for
     * @return Boolean for if hash is part of the Merkle path
     */
    /* TODO
    fun validateMerklePath(merklePath: MerklePath, targetTxHash: ByteArray): Boolean {
        return validateMerklePath(cryptoSystem, merklePath, targetTxHash, blockHeaderRec.getMerkleRootHash())
    }
     */


}



