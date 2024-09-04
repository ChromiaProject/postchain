// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseBlockHeaderTest {
    val blockchainRID = BlockchainRid.ZERO_RID
    val prevBlockRID0 = ByteArray(32, { if (it == 31) 99 else 0 }) // This is incorrect. Should include 99 at the end
    val cryptoSystem = Secp256K1CryptoSystem()
    val merkeHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    @Test
    fun makeHeaderWithCchainId0() {
        val prevBlockRid = ByteArray(32)
        // BlockchainId=0 should be allowed.
        val header0 = createBlockHeader(blockchainRID, 2L, 0, prevBlockRid, 0, merkeHashCalculator)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    @Test
    fun decodeMakeHeaderChainIdMax() {
        val prevBlockRid = ByteArray(24)+ByteArray(8, {if (it==0) 127 else -1})

        val header0 = createBlockHeader(blockchainRID, 2L, Long.MAX_VALUE, prevBlockRid, 0, merkeHashCalculator)

        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    @Test
    fun seeIfAllDependenciesArePresent() {
        val headerRaw = createBlockHeader(blockchainRID, 2L, 0, prevBlockRID0, 0, merkeHashCalculator)

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


}