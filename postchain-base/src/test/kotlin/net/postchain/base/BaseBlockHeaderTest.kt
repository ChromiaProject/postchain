// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseBlockHeaderTest {
    val blockchainRID = BlockchainRid.ZERO_RID
    val prevBlockRID0 = ByteArray(32, {if (it==31) 99 else 0}) // This is incorrect. Should include 99 at the end
    val cryptoSystem = Secp256K1CryptoSystem()
    val merkeHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    @Test
    fun makeHeaderWithCchainId0() {
        val prevBlockRid = ByteArray(32)
        // BlockchainId=0 should be allowed.
        val header0 = createHeader(blockchainRID,2L, 0, prevBlockRid, 0)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    @Test
    fun decodeMakeHeaderChainIdMax() {
        val prevBlockRid = ByteArray(24)+ByteArray(8, {if (it==0) 127 else -1})

        val header0 = createHeader(blockchainRID,2L, Long.MAX_VALUE, prevBlockRid, 0)

        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    @Test
    fun seeIfAllDependenciesArePresent() {
        val headerRaw = createHeader(blockchainRID,2L, 0, prevBlockRID0, 0)

        val decodedHeader = BaseBlockHeader(headerRaw.rawData, merkeHashCalculator)

        assertTrue(
                decodedHeader.checkCorrectNumberOfDependencies(listOf(
                BlockchainRelatedInfo( BlockchainRid.buildRepeat(1), "hello", 1L),
                BlockchainRelatedInfo( BlockchainRid.buildRepeat(2), "World", 2L)
                ).size)
        )
        assertFalse(
                decodedHeader.checkCorrectNumberOfDependencies(listOf(
                BlockchainRelatedInfo( BlockchainRid.buildRepeat(1), "hello", 1L),
                BlockchainRelatedInfo( BlockchainRid.buildRepeat(2), "cruel", 2L),
                BlockchainRelatedInfo( BlockchainRid.buildRepeat(3), "World", 3L)
                ).size)
        )
    }

    private fun createHeader(blockchainRid: BlockchainRid, blockIID: Long, chainId: Long, prevBlockRid: ByteArray, height: Long): BlockHeader {
        val rootHash = ByteArray(32, {0})
        val timestamp = 10000L + height
        val dependencies = createBlockchainDependencies()
        val blockData = InitialBlockData(blockchainRid, blockIID, chainId, prevBlockRid, height, timestamp, dependencies)
        return BaseBlockHeader.make(merkeHashCalculator, blockData, rootHash, timestamp, mapOf())
    }

    private fun createBlockchainDependencies(): Array<Hash?>? {
        val dummyHash1 = ByteArray(32, {1})
        val dummyHash2 = ByteArray(32, {2})
        return arrayOf(dummyHash1, dummyHash2)
    }
}