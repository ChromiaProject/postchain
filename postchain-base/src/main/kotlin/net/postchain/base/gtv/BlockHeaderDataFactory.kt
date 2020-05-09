// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.gtv

import net.postchain.base.merkle.Hash
import net.postchain.core.InitialBlockData
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv

object BlockHeaderDataFactory {

    fun buildFromBinary(rawData: ByteArray): BlockHeaderData {
        val gtv: Gtv = GtvDecoder.decodeGtv(rawData)
        return buildFromGtv(gtv)
    }

    fun buildFromGtv(gtvBlockHeader: Gtv): BlockHeaderData {
        val gtvMainArr = gtvBlockHeader as GtvArray

        // Fill it up some descriptive variables (not really needed but...)
        val gtvBlockchainRid: GtvByteArray = gtvMainArr[0] as GtvByteArray
        val previousBlockRid: GtvByteArray = gtvMainArr[1] as GtvByteArray
        val merkleRootHash: GtvByteArray = gtvMainArr[2] as GtvByteArray
        val snapshotMerkleRootHash: GtvByteArray = gtvMainArr[3] as GtvByteArray
        val timestamp: GtvInteger = gtvMainArr[4] as GtvInteger
        val height: GtvInteger = gtvMainArr[5] as GtvInteger
        val dependencies: Gtv = gtvMainArr[6]  // Could be GtvNull
        val extra: GtvDictionary = gtvMainArr[7] as GtvDictionary

        return BlockHeaderData(gtvBlockchainRid, previousBlockRid, merkleRootHash, snapshotMerkleRootHash, timestamp, height, dependencies, extra)
    }

    fun buildFromDomainObjects(iBlockData: InitialBlockData, rootHash: ByteArray, snapshotRootHash: ByteArray, timestamp: Long): BlockHeaderData {
        val gtvBlockchainRid: GtvByteArray = gtv(iBlockData.blockchainRid.data)
        val previousBlockRid: GtvByteArray = gtv(iBlockData.prevBlockRID)
        val merkleRootHash: GtvByteArray = gtv(rootHash)
        val snapshotMerkleRootHash: GtvByteArray = gtv(snapshotRootHash)
        val gtvTimestamp: GtvInteger = gtv(timestamp)
        val height: GtvInteger = gtv(iBlockData.height)
        val dependencies: Gtv = translateArrayOfHashToGtv(iBlockData.blockHeightDependencyArr)
        val extra = GtvDictionary.build(mapOf())

        return BlockHeaderData(gtvBlockchainRid, previousBlockRid, merkleRootHash, snapshotMerkleRootHash, gtvTimestamp, height, dependencies, extra)
    }

    private fun translateArrayOfHashToGtv(hashArr: Array<Hash?>?): Gtv {
        return if (hashArr != null) {
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