// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.gtv

import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.core.BadBlockException
import net.postchain.core.block.InitialBlockData
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull

/**
 * The structure of the block header goes like this:
 *
 *  1. blockchainRid [GtvByteArray]
 *  2. prevBlockRid [GtvByteArray]
 *  3. rootHash [GtvByteArray]
 *  4. timestamp [GtvInteger]
 *  5. height [GtvInteger]
 *  6. dependencies [GtvArray] or [GtvNull] (this is to save space)
 *  7. extra  [GtvDictionary]
 */
data class BlockHeaderData(
        val gtvBlockchainRid: GtvByteArray,
        val gtvPreviousBlockRid: GtvByteArray,
        val gtvMerkleRootHash: GtvByteArray,
        val gtvTimestamp: GtvInteger,
        val gtvHeight: GtvInteger,
        val gtvDependencies: Gtv, // Can be either GtvNull or GtvArray
        val gtvExtra: GtvDictionary) {


    fun getBlockchainRid(): ByteArray {
        return gtvBlockchainRid.bytearray
    }

    fun getPreviousBlockRid(): ByteArray {
        return gtvPreviousBlockRid.bytearray
    }

    fun getMerkleRootHash(): ByteArray {
        return gtvMerkleRootHash.bytearray
    }

    fun getTimestamp(): Long {
        return gtvTimestamp.integer
    }

    fun getHeight(): Long {
        return gtvHeight.integer
    }

    /**
     * Turns the [gtvDependencies] into an array of [Hash].
     *
     * Note that empty BC dependencies are allowed. This means that the BC we depend on has no blocks.
     * (We allow this bc it's easier to get started, specially during test)
     */
    fun getBlockHeightDependencyArray(): Array<Hash?> {
        return when (gtvDependencies) {
            is GtvNull -> arrayOf()
            is GtvArray -> {
                val lastBlockRidArray = arrayOfNulls<Hash>(gtvDependencies.getSize())
                for ((i, blockRid) in gtvDependencies.array.withIndex()) {
                    lastBlockRidArray[i] = when (blockRid) {
                        is GtvByteArray -> blockRid.bytearray
                        is GtvNull -> null // Allowed
                        else -> throw UserMistake("Cannot use type ${blockRid.type} in dependency list (at pos: $i)")
                    }
                }
                lastBlockRidArray
            }

            else -> throw BadBlockException("Header data has incorrect format in dependency part, where we found type: ${gtvDependencies.type}")
        }

    }

    fun getExtra(): Map<String, Gtv> {
        return gtvExtra.asDict()
    }

    fun toGtv(): GtvArray {
        return gtv(gtvBlockchainRid, gtvPreviousBlockRid, gtvMerkleRootHash, gtvTimestamp, gtvHeight, gtvDependencies, gtvExtra)
    }

    companion object {
        fun fromBinary(rawData: ByteArray) = fromGtv(GtvDecoder.decodeGtv(rawData))

        fun fromGtv(gtv: Gtv) = BlockHeaderData(
                gtv[0] as GtvByteArray,
                gtv[1] as GtvByteArray,
                gtv[2] as GtvByteArray,
                gtv[3] as GtvInteger,
                gtv[4] as GtvInteger,
                gtv[5],
                gtv[6] as GtvDictionary)

        fun fromDomainObjects(iBlockData: InitialBlockData, rootHash: ByteArray, timestamp: Long, extraData: Map<String, Gtv>): BlockHeaderData {
            val gtvBlockchainRid: GtvByteArray = gtv(iBlockData.blockchainRid.data)
            val previousBlockRid: GtvByteArray = gtv(iBlockData.prevBlockRID)
            val merkleRootHash: GtvByteArray = gtv(rootHash)
            val gtvTimestamp: GtvInteger = gtv(timestamp)
            val height: GtvInteger = gtv(iBlockData.height)
            val dependencies: Gtv = translateArrayOfHashToGtv(iBlockData.blockHeightDependencyArr)
            val extra = GtvDictionary.build(extraData)

            return BlockHeaderData(gtvBlockchainRid, previousBlockRid, merkleRootHash, gtvTimestamp, height, dependencies, extra)
        }

        private fun translateArrayOfHashToGtv(hashArr: Array<Hash?>?): Gtv = if (hashArr != null) {
            gtv(hashArr.map {
                if (it != null) {
                    gtv(it)
                } else {
                    GtvNull
                }
            })
        } else {
            GtvNull
        }
    }
}
