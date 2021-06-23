package net.postchain.base

import net.postchain.common.hexStringToByteArray
import net.postchain.devtools.KeyPairHelper.privKey
import net.postchain.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.gtxml.GTXMLTransactionParser
import net.postchain.gtx.gtxml.TransactionContext
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.test.assertTrue

class SECP256K1Test {

    @Test
    fun testEcrecover() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/auto-sign/tx_timeb.xml").readText()
        val cs = SECP256K1CryptoSystem()
        val tx = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(null),
            cs
        )
        val pubKey0 = pubKey(0)
        val privKey0 = privKey(0)
        val pubKey1 = pubKey(2)
        val privKey1 = privKey(2)
        val sigMaker0 = cs.buildSigMaker(pubKey0, privKey0)
        val sigMaker1 = cs.buildSigMaker(pubKey1, privKey1)

        // Signing
        val merkleRoot = tx.transactionBodyData.calculateRID(GtvMerkleHashCalculator(cs))
        val signature0 = sigMaker0.signDigest(merkleRoot)
        val signature1 = sigMaker1.signDigest(merkleRoot)
        val sig0 = secp256k1_decodeSignature(signature0.data)
        val sig1 = secp256k1_decodeSignature(signature1.data)

        // TODO:  SECP256K1Keccak tests
        /*
        val expected0 = decompressKey(pubKey0)
        val actual0 = SECP256K1Keccak.ecrecover(0, merkleRoot, sig0[0], sig0[1])!!
        assertTrue((expected0).contentEquals(actual0))

        val expected1 = decompressKey(pubKey1)
        val actual1 = SECP256K1Keccak.ecrecover(0, merkleRoot, sig1[0], sig1[1])!!
        assertTrue((expected1).contentEquals(actual1))
         */
    }

    @Test
    fun testEncodeSigWithV() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/auto-sign/tx_timeb.xml").readText()
        val cs = SECP256K1CryptoSystem()
        val tx = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(null),
            cs
        )
        val pubKey0 = pubKey(0)
        val privKey0 = privKey(0)
        val pubKey1 = pubKey(2)
        val privKey1 = privKey(2)
        val sigMaker0 = cs.buildSigMaker(pubKey0, privKey0)
        val sigMaker1 = cs.buildSigMaker(pubKey1, privKey1)

        // Signing
        val merkleRoot = tx.transactionBodyData.calculateRID(GtvMerkleHashCalculator(cs))
        val signature0 = sigMaker0.signDigest(merkleRoot)
        val signature1 = sigMaker1.signDigest(merkleRoot)
        val sig1 = secp256k1_decodeSignature(signature1.data)

        /*
        val expected0 = signature0.data.plus(ByteBuffer.allocate(1).put("1b".hexStringToByteArray()).array())
        val actual0 = encodeSignatureWithV(merkleRoot, pubKey0, signature0.data)
        assertTrue(expected0.contentEquals(actual0))

        val expected1 = encodeSignature(sig1[0], sig1[1], 27)
        val actual1 = encodeSignatureWithV(merkleRoot, pubKey1, signature1.data)
        assertTrue(expected1.contentEquals(actual1))

         */
    }
/*
    @Test
    fun testEncodeSigWithV2() {
        val blockRid = "335ddc617b75753c84873901784329c30ffe614cfcf1ce429a38c058545a8081".hexStringToByteArray()
        val pubkey = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57".hexStringToByteArray()
        val privkey = "3132333435363738393031323334353637383930313233343536373839303131".hexStringToByteArray()

        val cs = SECP256K1CryptoSystem()
        val sm = cs.buildSigMaker(pubkey, privkey)
        val sig = secp256k1_decodeSignature(sm.signDigest(blockRid).data)
        val sigc = encodeSignature(sig[0], sig[1], 27)
        val sigv = encodeSignatureWithV(blockRid, pubkey, sm.signDigest(blockRid).data)

        assertTrue(sigc.contentEquals(sigv))
    }*/
}