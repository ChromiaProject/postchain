// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder.Companion.PRIMARY_HEADER_KEY
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.data.Hash
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.InitialBlockData
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkleHash

/**
 * BaseBlockHeader implements elements and functionality that are necessary to describe and operate on a block header
 *
 * @property rawData DER encoded data including the previous blocks RID ([prevBlockRID]) and [timestamp]
 * @property timestamp  Specifies the time that a block was created as the number
 *                      of milliseconds since midnight January 1st 1970 UTC
 */
class BaseBlockHeader(override val rawData: ByteArray, merkleHashCalculator: GtvMerkleHashCalculatorBase) : BlockHeader {
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

    fun checkPrimaryExtraHeader(subjects: Array<ByteArray>): Boolean {
        val primaryHeader = extraData[PRIMARY_HEADER_KEY]
        return if (primaryHeader is GtvByteArray) {
            val primaryKey = primaryHeader.asByteArray()
            subjects.any { it.contentEquals(primaryKey) }
        } else false
    }

    companion object Factory {
        /**
         * Utility to simplify creating an instance of BaseBlockHeader
         *
         * @param iBlockData Initial block data including previous block identifier, timestamp and height
         * @param rootHash Merkle tree root hash
         * @param timestamp timestamp
         * @param extraData
         * @return Serialized block header
         */
        @JvmStatic
        fun make(
                merkleHashCalculator: GtvMerkleHashCalculatorBase,
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
}
