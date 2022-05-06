// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

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

    fun toGtv(): GtvArray {
        return GtvFactory.gtv(
            gtvBlockchainRid,
            gtvPreviousBlockRid,
            gtvMerkleRootHash,
            gtvTimestamp,
            gtvHeight,
            gtvDependencies,
            gtvExtra
        )
    }
}