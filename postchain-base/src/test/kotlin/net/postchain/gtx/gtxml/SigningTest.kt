// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.base.CURVE
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.SECP256K1KeccakCryptoSystem
import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper.privKey
import net.postchain.devtools.KeyPairHelper.privKeyHex
import net.postchain.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.KeyPairHelper.pubKeyHex
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningTest {

    @Test
    fun autoSign_autosigning_for_empty_signatures_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/auto-sign/tx_timeb.xml").readText()

        val cs = SECP256K1CryptoSystem()
        val tx = GTXMLTransactionParser.parseGTXMLTransaction(
                xml,
                TransactionContext(null),
                cs
        )

        val pubKey0 = pubKey(0)
        val privKey0 = privKey(0)
        val pubKey1 = pubKey(1)
        val privKey1 = privKey(1)
        val sigMaker0 = cs.buildSigMaker(pubKey0, privKey0)
        val sigMaker1 = cs.buildSigMaker(pubKey1, privKey1)

        val k = SECP256K1KeccakCryptoSystem()
        println("pubkey 0: ${pubKeyHex(0)}")
        println("privkey 0: ${privKeyHex(0)}")
        val hashedPubKey0 = k.digest(CURVE.curve.decodePoint(pubKey0).getEncoded(false).takeLast(64).toByteArray())
        val address0 = hashedPubKey0.takeLast(20).toByteArray()
        val hashedPubKey1 = k.digest(CURVE.curve.decodePoint(pubKey1).getEncoded(false).takeLast(64).toByteArray())
        val address1 = hashedPubKey1.takeLast(20).toByteArray()
        println("ethereum address 0: ${address0.toHex()}")
        println("ethereum address 1: ${address1.toHex()}")

        // Signing
        val merkleRoot = tx.transactionBodyData.calculateRID(GtvMerkleHashCalculator(cs))
        println("merkleRoot: ${merkleRoot.toHex()}")
        val signature0 = sigMaker0.signDigest(merkleRoot)
        val signature1 = sigMaker1.signDigest(merkleRoot)
        println("sig0: ${signature0.data.toHex()}")
        println("sig1: ${signature1.data.toHex()}")

        val verify = cs.verifyDigest(merkleRoot, signature0)
        assertTrue(verify)
    }

}