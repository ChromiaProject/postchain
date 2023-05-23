package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class BaseBlockHeaderTestMerkleProofTest {
    val blockchainRID = BlockchainRid.ZERO_RID

    val cryptoSystem = Secp256K1CryptoSystem()
    val merkeHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    val prevBlockRid = ByteArray(32)
    val wrappedProveTx = WrappedByteArray.fromHex("12341234")

    @Test
    fun proof_test_first() {
        val header0 = createBlockHeader(blockchainRID, 2L, 0, prevBlockRid, 0, merkeHashCalculator)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)

        val wrappedTxList = arrayOf(
                wrappedProveTx,
                WrappedByteArray.fromHex("11112222"),
                WrappedByteArray.fromHex("22223333"),
                WrappedByteArray.fromHex("33334444")
        )
        val ret = decodedHeader0.merkleProofTree(wrappedProveTx, wrappedTxList)

        assertEquals(ret.first, 0L)
    }

    @Test
    fun proof_test_last() {
        val header0 = createBlockHeader(blockchainRID, 2L, 0, prevBlockRid, 0, merkeHashCalculator)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)

        val wrappedTxList = arrayOf(
                WrappedByteArray.fromHex("11112222"),
                WrappedByteArray.fromHex("22223333"),
                WrappedByteArray.fromHex("33334444"),
                wrappedProveTx
        )
        val ret = decodedHeader0.merkleProofTree(wrappedProveTx, wrappedTxList)

        assertEquals(ret.first, 3L)
    }

    @Test
    fun proof_test_mid() {
        val header0 = createBlockHeader(blockchainRID, 2L, 0, prevBlockRid, 0, merkeHashCalculator)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, merkeHashCalculator)

        val wrappedTxList = arrayOf(
                WrappedByteArray.fromHex("11112222"),
                WrappedByteArray.fromHex("22223333"),
                wrappedProveTx,
                WrappedByteArray.fromHex("33334444")
        )
        val ret = decodedHeader0.merkleProofTree(wrappedProveTx, wrappedTxList)

        assertEquals(ret.first, 2L)
    }
}