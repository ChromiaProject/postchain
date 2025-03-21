package net.postchain.gtx

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import org.junit.jupiter.api.Test

class SignTest {
    val cryptoSystem = Secp256K1CryptoSystem()
    val hashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    val blockchainRid = BlockchainRid.buildRepeat(1)

    val alicePubkey = "038f888dec563b5bc253e87abc90afd26c3287021d10236ea19d248043dc39e0b8".hexStringToByteArray()
    val alicePrivkey = "71b5b7f8de0661af934a5e4612f3d0ba183e639bdf4e7452fb6457ed3cfbc825".hexStringToByteArray()
    val aliceKeyPair = KeyPair(alicePubkey, alicePrivkey)

    val bobPubkey = "02E0A8A3C79C9F18B7CEAD2493435AC926B4A527EF670B873F5F1410084EFF9C80".hexStringToByteArray()
    val bobPrivkey = "B31AB878C62B0E940B345C659A456D3573CF25960823C34C7BEEB5D1F813BEFD".hexStringToByteArray()
    val bobKeyPair = KeyPair(bobPubkey, bobPrivkey)

    val charliePubkey = "02620EB55BF0E3116F95D4D21771313AE5A477D5D166787DD8586D4413E3405D7E".hexStringToByteArray()
    val charliePrivkey = "944D38EA36E0FA2D9862D77F99874D88FD172559E56913DA55578740573B19ED".hexStringToByteArray()
    val charlieKeyPair = KeyPair(charliePubkey, charliePrivkey)

    @Test
    fun signingMultiSigTransaction() {
        val builder = GtxBuilder(
                blockchainRid,
                signers = listOf(alicePubkey, bobPubkey),
                cryptoSystem,
                hashCalculator
        )
                .addOperation("foo", gtv("bar"))
                .addNop()
                .uncheckedSignBuilder()
        val txRid: Hash = builder.txRid
        val partiallySignedTx: ByteArray = builder
                .sign(aliceKeyPair)
                .buildGtx().encode()

        assertThat(builder.isFullySigned()).isFalse()

        val fullySignedTx: ByteArray = signTransaction(partiallySignedTx, txRid = txRid, listOf(bobKeyPair), cryptoSystem)

        val gtx = Gtx.decode(fullySignedTx)
        gtx.signatures.forEachIndexed { index, signature ->
            assertThat(cryptoSystem.verifyDigest(txRid, Signature(gtx.gtxBody.signers[index], signature))).isTrue()
        }
    }

    @Test
    fun signingMultiSigTransactionAgain() {
        val builder = GtxBuilder(
                blockchainRid,
                signers = listOf(alicePubkey, bobPubkey),
                cryptoSystem,
                hashCalculator
        )
                .addOperation("foo", gtv("bar"))
                .addNop()
                .uncheckedSignBuilder()
        val txRid: Hash = builder.txRid
        val partiallySignedTx: ByteArray = builder
                .sign(aliceKeyPair)
                .buildGtx().encode()

        assertThat(builder.isFullySigned()).isFalse()

        assertFailure {
            signTransaction(partiallySignedTx, txRid = txRid, listOf(aliceKeyPair, bobKeyPair), cryptoSystem)
        }.isInstanceOf(UserMistake::class).messageContains("Signature for this signer already exists")
    }

    @Test
    fun signingMultiSigTransactionWithAnySigningOrder() {
        val builder = GtxBuilder(
                blockchainRid,
                signers = listOf(alicePubkey, bobPubkey, charliePubkey),
                cryptoSystem,
                hashCalculator
        )
                .addOperation("foo", gtv("bar"))
                .addNop()
                .uncheckedSignBuilder()
        val txRid: Hash = builder.txRid
        val charlieSignedTx: ByteArray = builder
                .sign(charlieKeyPair)
                .buildGtx().encode()

        assertThat(builder.isFullySigned()).isFalse()

        val aliceSignedTx: ByteArray = signTransaction(charlieSignedTx, txRid = txRid, listOf(aliceKeyPair), cryptoSystem)

        val fullySignedTx: ByteArray = signTransaction(aliceSignedTx, txRid = txRid, listOf(bobKeyPair), cryptoSystem)

        val gtx = Gtx.decode(fullySignedTx)
        gtx.signatures.forEachIndexed { index, signature ->
            assertThat(cryptoSystem.verifyDigest(txRid, Signature(gtx.gtxBody.signers[index], signature))).isTrue()
        }
    }
}
