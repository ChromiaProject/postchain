package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.InitialBlockData
import net.postchain.gtv.merkle.GtvMerkleHashCalculator

fun createBlockHeader(
        blockchainRid: BlockchainRid, blockIID: Long, chainId: Long, prevBlockRid: ByteArray, height: Long,
        merkeHashCalculator: GtvMerkleHashCalculator
): BlockHeader {
    val rootHash = ByteArray(32) { 0 }
    val timestamp = 10000L + height
    val dependencies = createBlockchainDependencies()
    val blockData = InitialBlockData(blockchainRid, blockIID, chainId, prevBlockRid, height, timestamp, dependencies)
    return BaseBlockHeader.make(merkeHashCalculator, blockData, rootHash, timestamp, mapOf())
}

private fun createBlockchainDependencies(): Array<Hash?> {
    val dummyHash1 = ByteArray(32) { 1 }
    val dummyHash2 = ByteArray(32) { 2 }
    return arrayOf(dummyHash1, dummyHash2)
}
