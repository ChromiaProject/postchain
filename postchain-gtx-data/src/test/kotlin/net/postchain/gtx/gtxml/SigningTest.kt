// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SigningTest {

    @Test
    fun autoSign_autosigning_for_empty_signatures_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/auto-sign/tx_timeb.xml").readText()

        val cs = Secp256K1CryptoSystem()
        val tx = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(null),
            cs
        )

        val pubKey = pubKey(0)
        val privKey = privKey(0)
        val sigMaker = cs.buildSigMaker(KeyPair(pubKey, privKey))

//        println(pubKey.toHex())
        assertEquals("03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070", tx.gtxBody.signers[0].toHex())

        // Signing
        val merkleRoot = tx.calculateTxRid(GtvMerkleHashCalculator(cs))
        val signature = sigMaker.signDigest(merkleRoot)
//        println(signature.data.toHex())

        val verify = cs.verifyDigest(merkleRoot, signature)
        assertTrue(verify)
    }

}