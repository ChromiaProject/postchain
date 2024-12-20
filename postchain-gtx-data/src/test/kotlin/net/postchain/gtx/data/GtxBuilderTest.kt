// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.data

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class GtxBuilderTest {

    private val crypto = Secp256K1CryptoSystem()
    private val calc = GtvMerkleHashCalculatorV2(::sha256Digest)

    private val signerPub = (0..3).map(::pubKey)
    private val signerPriv = (0..3).map(::privKey)

    private fun addOperations(b: GtxBuilder, signer: ByteArray?) {
        // primitives
        b.addOperation("hello", GtvNull, gtv(42), gtv("Wow"), gtv(signer ?: ByteArray(0)))
        // array of primitives
        b.addOperation("bro", gtv(GtvNull, gtv(2), gtv("Nope")))
        // dict
        b.addOperation("dictator", gtv(mapOf("two" to gtv(2), "five" to GtvNull)))
        // complex structure
        b.addOperation("soup",
                // map with array
                gtv(mapOf("array" to gtv(gtv(1), gtv(2), gtv(3)))),
                // array with map
                gtv(gtv(mapOf("inner" to gtv("space"))), GtvNull)
        )
    }

    @Test
    fun testGTXData() {
        val b = GtxBuilder(BlockchainRid.buildRepeat(0), signerPub.slice(0..2), crypto, calc)
        addOperations(b, signerPub[0])
        val txBuilder = b.finish()
                .sign(crypto.buildSigMaker(KeyPair(signerPub[0], signerPriv[0])))

        // try recreating from a serialized copy
        assertThrows<IllegalArgumentException> {
            txBuilder.buildGtx()
        }
        val sigMaker = crypto.buildSigMaker(KeyPair(signerPub[1], signerPriv[1]))
        val txBodyMerkleRoot = txBuilder.txRid
        val signature = sigMaker.signDigest(txBodyMerkleRoot)
        txBuilder.sign(signature)
        assertThrows<UserMistake> {  // Should not accept duplicate signatures
            txBuilder.sign(signature)
        }
        assertThrows<UserMistake> {
            val signature1 = Signature(signerPub[2], signerPub[2])
            txBuilder.sign(signature1)
        }

        assertThrows<UserMistake> {  // Allows signature from wrong participant
            val signatureMaker = crypto.buildSigMaker(KeyPair(signerPub[3], signerPriv[3]))
            val wrongSignature = signatureMaker.signDigest(txBodyMerkleRoot)
            txBuilder.sign(wrongSignature)
        }

        val sigMaker2 = crypto.buildSigMaker(KeyPair(signerPub[2], signerPriv[2]))
        txBuilder.sign(sigMaker2)

        assertTrue(txBuilder.isFullySigned())

        val d = Gtx.decode(txBuilder.buildGtx().encode())
        val body = d.gtxBody

        assertTrue(body.signers.toTypedArray().contentDeepEquals(
                signerPub.slice(0..2).toTypedArray()
        ))
        assertEquals(3, d.signatures.size)
        assertEquals(4, body.operations.size)
        assertEquals("bro", body.operations[1].opName)
        val op0 = body.operations[0]
        assertTrue(op0.args[0].isNull())
        assertEquals(42, op0.args[1].asInteger())
        assertEquals("Wow", op0.args[2].asString())
        assertTrue(op0.args[3].asByteArray().contentEquals(signerPub[0]))
        val op1 = body.operations[1]
        assertEquals("Nope", op1.args[0][2].asString())
        val dict2 = body.operations[2].args[0]
        assertEquals(2, dict2["two"]!!.asInteger())
        assertNull(dict2["six"])
        val mapWithArray = body.operations[3].args[0]
        assertEquals(2, mapWithArray["array"]!![1].asInteger())
        val arrayWithMap = body.operations[3].args[1]
        assertEquals("space", arrayWithMap[0]["inner"]!!.asString())
    }

    @Test
    fun sizeOne() {
        val b = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc, maxTxSize = 1000)
        b.addOperation("dictator", gtv(mapOf("two" to gtv(2), "five" to GtvNull)))
        checkSize(b, 1)
    }

    @Test
    fun sizeTwo() {
        val b = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc, maxTxSize = 1000)
        b.addOperation("dictator", gtv(mapOf("two" to gtv(2), "five" to GtvNull)))
        // complex structure
        b.addOperation("soup",
                // map with array
                gtv(mapOf("array" to gtv(gtv(1), gtv(2), gtv(3)))),
                // array with map
                gtv(gtv(mapOf("inner" to gtv("space"))), GtvNull)
        )
        checkSize(b, 2)
    }

    @Test
    fun sizeThree() {
        val b = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc, maxTxSize = 1000)
        b.addOperation("dictator", gtv(mapOf("two" to gtv(2), "five" to GtvNull)))
        // complex structure
        b.addOperation("soup",
                // map with array
                gtv(mapOf("array" to gtv(gtv(1), gtv(2), gtv(3)))),
                // array with map
                gtv(gtv(mapOf("inner" to gtv("space"))), GtvNull)
        )
        b.addOperation("desert", gtv(gtv("foo"), gtv(17)))
        checkSize(b, 3)
    }

    @Test
    fun sizeFour() {
        val b = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc, maxTxSize = 1000)
        addOperations(b, null)
        checkSize(b, 4)
    }

    @Test
    fun noFit() {
        val b = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc, maxTxSize = 250)
        b.addOperation("dictator", gtv(mapOf("two" to gtv(2), "five" to GtvNull)))
        assertThrows<IllegalStateException> {
            b.addOperation("soup",
                    // map with array
                    gtv(mapOf("array" to gtv(gtv(1), gtv(2), gtv(3)))),
                    // array with map
                    gtv(gtv(mapOf("inner" to gtv("space"))), GtvNull)
            )
        }
        checkSize(b, 1)
    }

    @Test
    fun signOverEmptySignature_given_valid_keypair() {
        val gtxBuilder = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc)

        val emptySign = gtxBuilder.uncheckedSignBuilder().emptySign(signerPub[0])

        val signOverEmptySignature = emptySign.signOverEmptySignature(crypto.buildSigMaker(KeyPair(signerPub[0], signerPriv[0])))
        assertEquals("4F36ACA7EFCABED6B4E3F82B0DE4B6B05E299CE420369F9FC37CCF2DB7F7551E596E12C402A833D3FDE2725F8D23F682BCFB6B94DC0D896E03D9056C05056765", signOverEmptySignature.buildGtx().signatures[0].toHex())
    }

    @Test
    fun signOverEmptySignature_given_faulty_keypair() {
        val gtxBuilder = GtxBuilder(BlockchainRid.buildRepeat(0), listOf(signerPub[0]), crypto, calc)

        val emptySign = gtxBuilder.uncheckedSignBuilder().emptySign(signerPub[0])

        assertThrows(TransactionIncorrect::class.java) {
            emptySign.signOverEmptySignature(crypto.buildSigMaker(KeyPair(signerPub[0], signerPriv[1])))
        }
    }

    private fun checkSize(b: GtxBuilder, numOps: Int) {
        val gtx = b.finish().sign(crypto.buildSigMaker(KeyPair(signerPub[0], signerPriv[0]))).buildGtx()
        assertEquals(numOps, gtx.gtxBody.operations.size)
        val actualSize = gtx.encode().size
        assertTrue(actualSize < b.maxTxSize)
        assertTrue(b.totalSize > actualSize) { "${b.totalSize} <= $actualSize" }
    }
}
