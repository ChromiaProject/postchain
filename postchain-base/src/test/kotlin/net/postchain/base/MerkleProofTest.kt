package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MerkleProofTest {
    val blockchainRID = BlockchainRid.ZERO_RID

    val cryptoSystem = Secp256K1CryptoSystem()
    val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    val wrappedProveTx = WrappedByteArray.fromHex("12341234")

    @Test
    fun proof_test_first() {
        val wrappedTxList = arrayOf(
                wrappedProveTx,
                WrappedByteArray.fromHex("11112222"),
                WrappedByteArray.fromHex("22223333"),
                WrappedByteArray.fromHex("33334444")
        )
        val ret = merkleProofTree(wrappedProveTx, wrappedTxList, merkleHashCalculator)

        assertEquals(ret.first, 0L)
    }

    @Test
    fun proof_test_last() {
        val wrappedTxList = arrayOf(
                WrappedByteArray.fromHex("11112222"),
                WrappedByteArray.fromHex("22223333"),
                WrappedByteArray.fromHex("33334444"),
                wrappedProveTx
        )
        val ret = merkleProofTree(wrappedProveTx, wrappedTxList, merkleHashCalculator)

        assertEquals(ret.first, 3L)
    }

    @Test
    fun proof_test_mid() {
        val wrappedTxList = arrayOf(
                WrappedByteArray.fromHex("11112222"),
                WrappedByteArray.fromHex("22223333"),
                wrappedProveTx,
                WrappedByteArray.fromHex("33334444")
        )
        val ret = merkleProofTree(wrappedProveTx, wrappedTxList, merkleHashCalculator)

        assertEquals(ret.first, 2L)
    }
}